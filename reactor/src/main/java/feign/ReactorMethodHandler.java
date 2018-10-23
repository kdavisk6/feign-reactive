package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.Logger.Level;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.util.Assert;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

/**
 * Method Handler implementation backed by the Project Reactor Reactive Streams implementation.
 */
public class ReactorMethodHandler implements MethodHandler {

  private Target<?> target;
  private MethodMetadata metadata;
  private ReactiveClient client;
  private Decoder decoder;
  private ErrorDecoder errorDecoder;
  private Logger logger;
  private Logger.Level logLevel;
  private Retryer retryer;
  private final ReactiveRequestTemplateFactory requestTemplateFactory;

  static Builder builder(Target<?> target, MethodMetadata methodMetadata) {
    return new Builder(target, methodMetadata);
  }

  private ReactorMethodHandler(Target<?> target,
                               MethodMetadata metadata,
                               Set<RequestInterceptor> interceptors,
                               ReactiveClient client,
                               Encoder encoder,
                               Decoder decoder,
                               ErrorDecoder errorDecoder,
                               Logger logger,
                               Level logLevel,
                               Retryer retryer,
                               QueryMapEncoder queryMapEncoder) {
    Assert.isNotNull(target, "target is required");
    Assert.isNotNull(metadata, "metadata is required");
    Assert.isNotNull(client, "client is required");
    Assert.isNotNull(encoder, "encoder is required");
    Assert.isNotNull(decoder, "decoder is required");
    Assert.isNotNull(errorDecoder, "errorDecoder is required");
    Assert.isNotNull(logger, "logger is required");
    Assert.isNotNull(retryer, "retryer is required");
    Assert.isNotNull(queryMapEncoder, "queryMapEncoder is required");
    this.target = target;
    this.metadata = metadata;
    this.client = client;
    this.decoder = decoder;
    this.errorDecoder = errorDecoder;
    this.logger = logger;
    this.logLevel = logLevel;
    this.retryer = retryer;
    this.requestTemplateFactory = new ReactorRequestTemplateFactory(this.metadata, interceptors,
        encoder, queryMapEncoder);
  }

  /**
   * Invoke the Target Method.  Any unhandled exceptions will short-circuit the pipeline.
   *
   * @param arguments for the Method.
   * @return a {@link org.reactivestreams.Publisher} for the Method return type.
   */
  @Override
  public Object invoke(Object[] arguments) {

    /* start the reactive pipeline */
    return Flux.from(this.requestTemplateFactory.create(arguments))

        /* encode and prepare the request */
        .map(template -> target.apply(template))

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
   * Method Handler Builder.
   */
  @SuppressWarnings("unused")
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
    private QueryMapEncoder queryMapEncoder;

    Builder(Target<?> target, MethodMetadata metadata) {
      this.target = target;
      this.metadata = metadata;
    }

    public Builder interceptor(RequestInterceptor interceptor) {
      this.interceptors.add(interceptor);
      return this;
    }

    Builder interceptors(Collection<RequestInterceptor> interceptors) {
      this.interceptors.addAll(interceptors);
      return this;
    }

    Builder client(ReactiveClient client) {
      this.client = client;
      return this;
    }

    Builder encoder(Encoder encoder) {
      this.encoder = encoder;
      return this;
    }

    Builder decoder(Decoder decoder) {
      this.decoder = decoder;
      return this;
    }

    Builder errorDecoder(ErrorDecoder errorDecoder) {
      this.errorDecoder = errorDecoder;
      return this;
    }

    Builder logger(Logger logger) {
      this.logger = logger;
      return this;
    }

    Builder retryer(Retryer retryer) {
      this.retryer = retryer;
      return this;
    }

    Builder logLevel(Logger.Level logLevel) {
      this.logLevel = logLevel;
      return this;
    }

    Builder queryMapEncoder(QueryMapEncoder queryMapEncoder) {
      this.queryMapEncoder = queryMapEncoder;
      return this;
    }

    ReactorMethodHandler build() {
      return new ReactorMethodHandler(this.target, this.metadata, this.interceptors, this.client,
          this.encoder, this.decoder, this.errorDecoder, this.logger,
          this.logLevel, this.retryer, this.queryMapEncoder);

    }
  }
}
