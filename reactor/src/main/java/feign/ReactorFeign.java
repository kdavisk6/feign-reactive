package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.Logger.Level;
import feign.Request.Options;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import java.lang.reflect.Method;
import java.util.Set;

public class ReactorFeign extends ReactiveFeign {

  /**
   * Create a new {@link ReactorFeign} instance.
   *
   * @param contract for the {@link Target}
   * @param client to use when executing {@link Target} requests.
   * @param encoder to use when preparing {@link Target} method Request entities.
   * @param decoder to use when reading {@link Target} method Response entities.
   * @param errorDecoder to use when an unexpected Response is returned.
   * @param logger to use to report on internal method processing.
   * @param logLevel for how much information to report when logging.
   * @param retryer to use in the event of a transient exception or unexpected Response.
   * @param interceptors to apply before executing the {@link Target} request method.
   * @param queryMapEncoder to use when expanding {@link QueryMap} annotated parameters.
   */
  private ReactorFeign(Contract contract,
                       ReactiveClient client,
                       Encoder encoder,
                       Decoder decoder,
                       ErrorDecoder errorDecoder,
                       Logger logger,
                       Level logLevel,
                       Retryer retryer,
                       Set<RequestInterceptor> interceptors,
                       QueryMapEncoder queryMapEncoder) {
    super(new ReactorInvocationHandlerFactory(), contract, client, encoder, decoder, errorDecoder,
        logger, logLevel, retryer, interceptors, queryMapEncoder);
  }

  /**
   * Create a new {@link ReactorMethodHandler}.
   *
   * @param target of the {@link Method}
   * @param methodMetadata describing the {@link Method}
   * @return a new {@link ReactorMethodHandler} instance.
   */
  @Override
  protected MethodHandler getMethodHandler(Target<?> target, MethodMetadata methodMetadata) {
    return ReactorMethodHandler.builder(target, methodMetadata)
        .client(this.client)
        .decoder(this.decoder)
        .encoder(this.encoder)
        .errorDecoder(this.errorDecoder)
        .interceptors(this.interceptors)
        .retryer(this.retryer)
        .logger(this.logger)
        .logLevel(this.logLevel)
        .queryMapEncoder(this.queryMapEncoder)
        .build();
  }

  /**
   * {@link ReactiveFeign} builder for Project Reactor based {@link Target}s
   */
  public static class Builder extends ReactiveFeign.Builder {

    /**
     * Create a new {@link Target} backed by a Project Reactor method handler.
     *
     * @param type of {@link Target}
     * @param url host for the {@link Target}
     * @param <T> type of the {@link Target} interface.
     * @return a JDK Proxy for the {@link Target} interface.
     */
    @Override
    public <T> T target(Class<T> type, String url) {
      if (this.client == null) {
        this.client = new DelegatingReactorClient(
            new Client.Default(null, null), new Options());
      }

      ReactiveFeign feign = new ReactorFeign(this.contract, this.client, this.encoder, this.decoder,
          this.errorDecoder, this.logger, this.logLevel, this.retryer, this.interceptors,
          this.queryMapEncoder);
      return feign.newInstance(new HardCodedTarget<>(type, url));
    }
  }
}
