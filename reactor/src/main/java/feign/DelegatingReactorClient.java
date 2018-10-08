package feign;

import feign.Request.Options;
import java.io.IOException;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/**
 * {@link ReactiveClient} that wraps a synchronous {@link Client} in a Reactor based
 * {@link Flux} publisher.
 */
public class DelegatingReactorClient implements ReactiveClient {

  private final Client delegate;
  private final Options options;

  /**
   * Create a new {@link ReactiveClient} for the delegate.
   *
   * @param delegate {@link Client} to wrap.
   * @param options for each {@link Request}
   */
  public DelegatingReactorClient(Client delegate, Options options) {
    this.delegate = delegate;
    this.options = options;
  }

  /**
   * Execute a new Synchronous request for each {@link Request} provided by the {@link Publisher}.
   * This is available for backward compatibility and to allow for use of existing {@link Client}
   * implementations without the blocking behavior present in core Feign.
   *
   * @param requests {@link Publisher} to subscribe to.
   * @return a {@link Publisher} for the corresponding {@link Response}s
   */
  @Override
  public Publisher<Response> execute(Publisher<Request> requests) {
    return Flux.from(requests)
        .map(request -> {
          try {
            return delegate.execute(request, options);
          } catch (IOException ioe) {
            throw new RetryableException(ioe.getMessage(), request.httpMethod(), ioe, null);
          }
        });
  }
}
