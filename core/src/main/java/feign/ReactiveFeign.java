package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.Logger.Level;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.util.Assert;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A <a href="https://reactive-streams.org">Reactive Streams</a> backed, non-blocking, backpressure
 * aware version of Feign.  Implementation of the underlying {@link Proxy} instances are delegated
 * to the specific reactive-streams compatible implementations.
 */
public abstract class ReactiveFeign {

  private final InvocationHandlerFactory invocationHandlerFactory;
  private final Contract contract;
  final ReactiveClient client;
  final Encoder encoder;
  final Decoder decoder;
  final ErrorDecoder errorDecoder;
  final Logger logger;
  final Logger.Level logLevel;
  final Retryer retryer;
  final Set<RequestInterceptor> interceptors;

  /**
   * Create a new {@link ReactiveFeign} instance.
   *
   * @param invocationHandlerFactory responsible for creating the {@link Target} {@link Proxy}s.
   * @param contract to use when parsing the {@link Target} interface.
   * @param client to use when executing the {@link Target} Request methods.
   * @param encoder to use when preparing Request entities.
   * @param decoder to use when handling Response entities.
   * @param errorDecoder to use when an unexpected Response is returned.
   * @param logger to use to report on internal method processing.
   * @param logLevel or how much information to report when logging.
   * @param retryer to use in the event of a transient exception or unexpected Response.
   * @param interceptors to apply before executing the {@link Target} request method.
   */
  ReactiveFeign(InvocationHandlerFactory invocationHandlerFactory,
                Contract contract,
                ReactiveClient client,
                Encoder encoder,
                Decoder decoder,
                ErrorDecoder errorDecoder,
                Logger logger,
                Level logLevel,
                Retryer retryer,
                Set<RequestInterceptor> interceptors) {
    Assert.isNotNull(invocationHandlerFactory, "invocationHandlerFactory is required.");
    Assert.isNotNull(contract, "contract is required.");
    this.invocationHandlerFactory = invocationHandlerFactory;
    this.contract = contract;
    this.client = client;
    this.encoder = encoder;
    this.decoder = decoder;
    this.errorDecoder = errorDecoder;
    this.logger = logger;
    this.logLevel = logLevel;
    this.retryer = retryer;
    this.interceptors = interceptors;
  }


  /**
   * Create a new {@link Target} instance.
   *
   * @param target to create.
   * @param <T> type of the {@link Target} interface.
   * @return a JDK Proxy for the {@link Target}.
   */
  @SuppressWarnings("unchecked")
  <T> T newInstance(Target<T> target) {
    Map<Method, MethodHandler> dispatch = this.parseTarget(target);
    InvocationHandler handler = this.invocationHandlerFactory.create(target, dispatch);
    return (T) Proxy.newProxyInstance(target.type().getClassLoader(),
        new Class<?>[] {target.type()}, handler);
  }

  /**
   * Examine the {@link Target} and map the {@link Target} {@link Method} to the
   * {@link MethodHandler} to be used by the proxy.
   *
   * @param target to examine.
   * @return a Map of {@link MethodHandler}s keyed to the {@link Target} {@link Method}.
   */
  private Map<Method, MethodHandler> parseTarget(Target<?> target) {
    Map<Method, MethodHandler> methodHandlerMap = new LinkedHashMap<>();

    /* map the method names to their metadata */
    Map<String, MethodMetadata> methodMetadataMap = this.parseMethodMetadata(target);

    /* associate the metadata with the the method */
    Method[] targetMethods = target.getClass().getMethods();
    for (Method targetMethod : targetMethods) {
      /* default methods are not mapped */
      if (!targetMethod.isDefault()) {
        String key = Feign.configKey(target.type(), targetMethod);
        if (methodMetadataMap.containsKey(key)) {
          MethodMetadata metadata = methodMetadataMap.get(key);
          methodHandlerMap.put(targetMethod, this.getMethodHandler(target, metadata));
        }
      }
    }
    return methodHandlerMap;
  }

  /**
   * Map the Method name to its corresponding {@link MethodMetadata}/
   *
   * @param target to parse.
   * @return a Map of {@link MethodMetadata} mapped to corresponding {@link Method} name.
   */
  private Map<String, MethodMetadata> parseMethodMetadata(Target<?> target) {
    Map<String, MethodMetadata> methodMetadataMap = new LinkedHashMap<>();

    /* parse the target using the supplied contract */
    List<MethodMetadata> methodMetadata = this.contract.parseAndValidatateMetadata(target.type());
    methodMetadata.forEach(
        metadata -> methodMetadataMap.put(metadata.configKey(), metadata));
    return methodMetadataMap;
  }

  /**
   * Create a {@link MethodHandler}.
   *
   * @param target of the {@link Method}
   * @param methodMetadata describing the {@link Method}
   * @return a {@link MethodHandler} for the proxy to use.
   */
  protected abstract MethodHandler getMethodHandler(Target<?> target,
                                                    MethodMetadata methodMetadata);


  /**
   * Builder for creating {@link ReactiveFeign} instances.
   */
  public abstract static class Builder {

    Contract contract = new Contract.Default();
    ReactiveClient client;
    Encoder encoder = new Encoder.Default();
    Decoder decoder = new Decoder.Default();
    ErrorDecoder errorDecoder = new ErrorDecoder.Default();
    Logger logger = new Logger.NoOpLogger();
    Logger.Level logLevel = Level.NONE;
    Retryer retryer = new Retryer.Default();
    Set<RequestInterceptor> interceptors = new LinkedHashSet<>();

    /**
     * Contract to use when parsing the {@link Target} interface.
     *
     * @param contract instance.
     * @return a Builder for chaining.
     */
    public Builder contract(Contract contract) {
      Assert.isNotNull(contract, "a Contract is required.");
      this.contract = contract;
      return this;
    }

    /**
     * {@link ReactiveClient} to use for all requests.
     *
     * @param client instance to use.
     * @return a Builder for chaining.
     */
    public Builder client(ReactiveClient client) {
      Assert.isNotNull(client, "a ReactiveClient is required.");
      this.client = client;
      return this;
    }

    /**
     * {@link Encoder} instance to use when preparing Request entities.
     *
     * @param encoder instance.
     * @return a Builder for chaining.
     */
    public Builder encoder(Encoder encoder) {
      Assert.isNotNull(encoder, "an Encoder is required.");
      this.encoder = encoder;
      return this;
    }

    /**
     * {@link Decoder} instance to use when parsing Response entities.
     *
     * @param decoder instance.
     * @return a Builder for chaining.
     */
    public Builder decoder(Decoder decoder) {
      Assert.isNotNull(decoder, "a Decoder is required.");
      this.decoder = decoder;
      return this;
    }

    /**
     * {@link ErrorDecoder} to use when an unexpected Response is returned.
     *
     * @param errorDecoder instance.
     * @return a Builder for chaining.
     */
    public Builder errorDecoder(ErrorDecoder errorDecoder) {
      Assert.isNotNull(errorDecoder, "an Error Decoder is required.");
      this.errorDecoder = errorDecoder;
      return this;
    }

    /**
     * {@link Logger} reference to use.
     *
     * @param logger instance.
     * @return a Builder for chaining.
     */
    public Builder logger(Logger logger) {
      Assert.isNotNull(logger, "a Logger is required.");
      this.logger = logger;
      return this;
    }

    /**
     * {@link Logger.Level} for the {@link Target}.  This controls Feign's internal logging
     * mechanism, not the level of the Logger itself.
     *
     * @param logLevel for the {@link Logger}
     * @return a Builder for chaining.
     */
    public Builder logLevel(Level logLevel) {
      Assert.isNotNull(logLevel, "a Log Level is required.");
      this.logLevel = logLevel;
      return this;
    }

    /**
     * {@link Retryer} to use when a Request cannot be sent due to either a Transient Exception or
     * an unexpected Response.
     *
     * @param retryer instance.
     * @return a Builder for chaining.
     */
    public Builder retryer(Retryer retryer) {
      Assert.isNotNull(retryer, "a Retryer instance is required.");
      this.retryer = retryer;
      return this;
    }

    /**
     * a {@link RequestInterceptor} to apply before each Request.
     *
     * @param requestInterceptor instance to apply.
     * @return a Builder for chaining.
     */
    public Builder interceptor(RequestInterceptor requestInterceptor) {
      this.interceptors.add(requestInterceptor);
      return this;
    }

    /**
     * Create the {@link Target} instance.
     *
     * @param type of {@link Target}
     * @param url host for the {@link Target}
     * @param <T> type of the {@link Target} interface.
     * @return a new JDK Proxy for the {@link Target} type.
     */
    public abstract <T> T target(Class<T> type, String url);
  }
}
