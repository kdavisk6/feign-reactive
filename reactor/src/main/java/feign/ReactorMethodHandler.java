package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.Logger.Level;
import feign.Param.Expander;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

/**
 * Method Handler implementation backed by the Project Reactor Reactive Streams implementation.
 */
public class ReactorMethodHandler implements MethodHandler {

  private Target<?> target;
  private MethodMetadata metadata;
  private Set<RequestInterceptor> interceptors;
  private ReactiveClient client;
  private Encoder encoder;
  private Decoder decoder;
  private ErrorDecoder errorDecoder;
  private Logger logger;
  private Logger.Level logLevel;
  private Retryer retryer;
  private Map<Integer, Expander> expanders = new LinkedHashMap<>();

  public static Builder builder(Target<?> target, MethodMetadata methodMetadata) {
    return new Builder(target, methodMetadata);
  }

  ReactorMethodHandler(Target<?> target,
                       MethodMetadata metadata,
                       Set<RequestInterceptor> interceptors,
                       ReactiveClient client,
                       Encoder encoder,
                       Decoder decoder,
                       ErrorDecoder errorDecoder,
                       Logger logger,
                       Level logLevel,
                       Retryer retryer) {
    this.target = target;
    this.metadata = metadata;
    this.interceptors = interceptors;
    this.client = client;
    this.encoder = encoder;
    this.decoder = decoder;
    this.errorDecoder = errorDecoder;
    this.logger = logger;
    this.logLevel = logLevel;
    this.retryer = retryer;
    this.cacheExpanders();
  }

  /**
   * Invoke the Target Method.  Any unhandled exceptions will short-circuit the pipeline.
   *
   * @param arguments for the Method.
   * @return a {@link org.reactivestreams.Publisher} for the Method return type.
   * @throws Throwable in the event that the Method could not be invoked properly.
   */
  @Override
  public Object invoke(Object[] arguments) throws Throwable {

    /* reference the template for this request */
    RequestTemplate requestTemplate = this.metadata.template();

    /* build the map of template variables from the method arguments, per the contract */
    Map<String, Object> variables = this.getVariableMapFromArguments(arguments);

    /* identify the request body from the arguments */
    final Object requestBody = this.getRequestBodyFromArguments(arguments);

    /* append any query and/or header map method parameters to the template */

    /* start the reactive pipeline */
    return Flux.just(requestTemplate)
        /* resolve the template, building the request specification */
        .map(template -> template.resolve(variables))

        /* process any registered request interceptors */
        .map(template -> {
          interceptors.forEach(requestInterceptor -> requestInterceptor.apply(template));
          return template;
        })

        /* encode and prepare the request */
        .map(template -> {
          try {
            return prepareRequest(requestBody, template);
          } catch (Throwable throwable) {
            throw Exceptions.propagate(throwable);
          }
        })

        /* log the request */
        .doOnNext(request -> logger.logRequest(metadata.configKey(), logLevel, request))

        /* execute the request */
        .transform(requestFlux -> client.execute(requestFlux))

        /* retry, if required */
        .retry(throwable -> retryer.shouldRetry(throwable))

        .map(response -> {
          try {
            return logger.logAndRebufferResponse(metadata.configKey(), logLevel, response, 0);
          } catch (IOException ioe) {
            throw Exceptions.propagate(ioe);
          }
        })

        /* map the response */
        .map(response -> {
          try {
            /* parse the response into the desired type */
            return parseResponse(response);
          } catch (Throwable throwable) {
            /* exceptions that occur here are the responsibility of the subscriber, make sure
             * that they reach them.
             */
            throw Exceptions.propagate(throwable);
          }
        });
  }

  /**
   * Locate and extract the argument that represents the body of the request.  Can be {@literal
   * null} if none are present.
   *
   * @param arguments of the method to inspect.
   * @return the argument that represents the body of the request.  Can be {@literal null}.
   */
  private Object getRequestBodyFromArguments(Object[] arguments) {
    List<Object> methodArguments = Arrays.asList(arguments);
    if (this.metadata.bodyIndex() != null) {
      return methodArguments.get(this.metadata.bodyIndex());
    }
    return null;
  }

  /**
   * Prepare the Request.
   *
   * @param body of the Request.
   * @param template for the Request.
   * @return a Request specification.
   */
  private Request prepareRequest(Object body, RequestTemplate template) {
    if (body != null) {
      /* encode the request body, adding it to the request template */
      encoder.encode(body, metadata.bodyType(), template);
    }

    /* target the template and return the request */
    return target.apply(template);
  }

  /**
   * Parse the Response body into the desired type.
   *
   * @param response to process.
   * @return the desired type, based on the method signature.
   * @throws Throwable if the response could not be decoded or if the Response returned an error
   * status.
   */
  private Object parseResponse(Response response) throws Throwable {
    if (response.isSuccessful()) {
      if (metadata.returnType() == Response.class) {
        return response;
      } else {
        try {
          return decoder.decode(response, metadata.returnType());
        } catch (IOException | DecodeException de) {
          throw new FeignException(de.getMessage(), de, response.request().body());
        }
      }
    } else {
      /* response was returned, but did not return a successful status, delegate response
       * handling to the error decoder.
       */
      throw errorDecoder.decode(metadata.configKey(), response);
    }
  }

  /**
   * Create the variable substitution map to based on the Method arguments.
   *
   * @param arguments to resolve.
   * @return a map of resolved template variables.
   */
  private Map<String, Object> getVariableMapFromArguments(Object[] arguments) {
    Map<String, Object> variables = new LinkedHashMap<>();
    Map<Integer, Collection<String>> parameterArguments = this.metadata.indexToName();
    for (Integer index : parameterArguments.keySet()) {
      final Object parameter = arguments[index];
      final Expander expander = this.expanders.get(index);
      Collection<String> parameterNames = parameterArguments.get(index);
      parameterNames.forEach(name -> {
        if (parameter != null) {
          Object value = parameter;
          if (expander != null) {
            value = expander.expand(value);
          }
          variables.put(name, value);
        }
      });
    }

    return variables;
  }

  private void cacheExpanders() {
    this.expanders = new LinkedHashMap<>(this.metadata.indexToExpander());
    if (!this.metadata.indexToExpanderClass().isEmpty()) {
      this.metadata.indexToExpanderClass().forEach(
          (index, expanderClass) -> {
            try {
              Expander expander = expanderClass.getDeclaredConstructor().newInstance();
              expanders.put(index, expander);
            } catch (Exception ex) {
              /* the expander does not have a default constructor and cannot be created */
              throw new IllegalStateException("Expander " + expanderClass.getName() +
                  " does not have a visible no-argument constructor. Expander cannot be created.",
                  ex);
            }
          });
    }
  }

  /**
   * Method Handler Builder.
   */
  public static class Builder {

    private Target<?> target;
    private MethodMetadata metadata;
    private Set<RequestInterceptor> interceptors = new LinkedHashSet<>();
    private ReactiveClient client;
    private Encoder encoder;
    private Decoder decoder;
    private ErrorDecoder errorDecoder;
    private Logger logger;
    private Logger.Level logLevel;
    private Retryer retryer;

    public Builder(Target<?> target, MethodMetadata metadata) {
      this.target = target;
      this.metadata = metadata;
    }

    public Builder interceptor(RequestInterceptor interceptor) {
      this.interceptors.add(interceptor);
      return this;
    }

    public Builder interceptors(Collection<RequestInterceptor> interceptors) {
      this.interceptors.addAll(interceptors);
      return this;
    }

    public Builder client(ReactiveClient client) {
      this.client = client;
      return this;
    }

    public Builder encoder(Encoder encoder) {
      this.encoder = encoder;
      return this;
    }

    public Builder decoder(Decoder decoder) {
      this.decoder = decoder;
      return this;
    }

    public Builder errorDecoder(ErrorDecoder errorDecoder) {
      this.errorDecoder = errorDecoder;
      return this;
    }

    public Builder logger(Logger logger) {
      this.logger = logger;
      return this;
    }

    public Builder retryer(Retryer retryer) {
      this.retryer = retryer;
      return this;
    }

    public Builder logLevel(Logger.Level logLevel) {
      this.logLevel = logLevel;
      return this;
    }

    public ReactorMethodHandler build() {
      return new ReactorMethodHandler(this.target, this.metadata, this.interceptors, this.client,
          this.encoder, this.decoder, this.errorDecoder, this.logger,
          this.logLevel, this.retryer);

    }
  }
}
