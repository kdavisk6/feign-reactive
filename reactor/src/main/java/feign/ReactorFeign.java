package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.Logger.Level;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReactorFeign extends Feign {

  private final InvocationHandlerFactory invocationHandlerFactory = new ReactorInvocationHandlerFactory();
  private final Contract contract;
  private final ReactiveClient client;
  private final Encoder encoder;
  private final Decoder decoder;
  private final ErrorDecoder errorDecoder;
  private final Logger logger;
  private final Logger.Level logLevel;
  private final Retryer retryer;
  private final Set<RequestInterceptor> interceptors;

  public ReactorFeign(Contract contract,
                      ReactiveClient client,
                      Encoder encoder,
                      Decoder decoder,
                      ErrorDecoder errorDecoder,
                      Logger logger,
                      Level logLevel,
                      Retryer retryer,
                      Set<RequestInterceptor> interceptors) {
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


  @SuppressWarnings("unchecked")
  @Override
  public <T> T newInstance(Target<T> target) {
    Map<Method, MethodHandler> dispatch = this.parseTarget(target);
    InvocationHandler handler = this.invocationHandlerFactory.create(target, dispatch);
    return (T) Proxy.newProxyInstance(target.type().getClassLoader(),
        new Class<?>[] {target.type()}, handler);
  }

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
  private ReactorMethodHandler getMethodHandler(Target<?> target, MethodMetadata methodMetadata) {
    return ReactorMethodHandler.builder(target, methodMetadata)
        .client(this.client)
        .decoder(this.decoder)
        .encoder(this.encoder)
        .errorDecoder(this.errorDecoder)
        .interceptors(this.interceptors)
        .retryer(this.retryer)
        .build();
  }

  public static class Builder {
    private Contract contract = new Contract.Default();
    private ReactiveClient client;
    private Encoder encoder = new Encoder.Default();
    private Decoder decoder = new Decoder.Default();
    private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
    private Logger logger = new Logger.NoOpLogger();
    private Logger.Level logLevel = Level.NONE;
    private Retryer retryer = new Retryer.Default();
    private Set<RequestInterceptor> interceptors = new LinkedHashSet<>();

    public Builder contract(Contract contract) {
      this.contract = contract;
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

    public Builder logLevel(Level logLevel) {
      this.logLevel = logLevel;
      return this;
    }

    public Builder retryer(Retryer retryer) {
      this.retryer = retryer;
      return this;
    }

    public Builder interceptor(RequestInterceptor requestInterceptor) {
      this.interceptors.add(requestInterceptor);
      return this;
    }

    public Target<?> target(Class<?> type, String url) {
      return null;
    }
  }
}
