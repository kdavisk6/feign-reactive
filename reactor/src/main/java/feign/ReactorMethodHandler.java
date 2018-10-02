package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Flux;

public class ReactorMethodHandler implements MethodHandler {

  private Target<?> target;
  private MethodMetadata metadata;
  private Set<RequestInterceptor> interceptors;
  private ReactiveClient client;
  private Encoder encoder;
  private Decoder decoder;
  private ErrorDecoder errorDecoder;
  private Logger logger;
  private Retryer retryer;

  ReactorMethodHandler(Target<?> target,
                       MethodMetadata metadata,
                       Set<RequestInterceptor> interceptors,
                       ReactiveClient client,
                       Encoder encoder,
                       Decoder decoder,
                       ErrorDecoder errorDecoder,
                       Logger logger,
                       Retryer retryer) {
    this.target = target;
    this.metadata = metadata;
    this.interceptors = interceptors;
    this.client = client;
    this.encoder = encoder;
    this.decoder = decoder;
    this.errorDecoder = errorDecoder;
    this.logger = logger;
    this.retryer = retryer;
  }

  @Override
  public Object invoke(Object[] argv) throws Throwable {

    /* todo add logging steps to pipeline */
    RequestTemplate requestTemplate = this.metadata.template();
    Map<String, Object> variables = this.getVariableMapFromArguments(argv);
    return Flux.just(requestTemplate)
        .map(template -> {
          interceptors.forEach(requestInterceptor -> requestInterceptor.apply(template));
          return template;
        })
        .map(template -> template.resolve(variables))
        .map(RequestTemplate::request)
        .compose(requestFlux -> client.execute(requestFlux))
        .retry(throwable -> retryer.shouldRetry(throwable))
        .map(response -> {
          try {
            return decoder.decode(response, metadata.returnType());
          } catch (IOException | DecodeException de) {
            throw new FeignException(de.getMessage(), de);
          }
        });
  }

  private Map<String, Object> getVariableMapFromArguments(Object[] argv) {
    return Collections.emptyMap();
  }

  public static class Builder {

    private Target<?> target;
    private MethodMetadata metadata;
    private Set<RequestInterceptor> interceptors = new LinkedHashSet<>();
    private ReactiveClient client;
    private Encoder encoder;
    private Decoder decoder;
    private ErrorDecoder errorDecoder;
    private Logger logger;
    private Set<ResponseMapper> responseMappers = new LinkedHashSet<>();
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

    public ReactorMethodHandler build() {
      return new ReactorMethodHandler(this.target, this.metadata, this.interceptors, this.client,
          this.encoder, this.decoder, this.errorDecoder, this.logger,
          this.retryer);

    }
  }
}
