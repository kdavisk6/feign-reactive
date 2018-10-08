package feign;

import feign.Request.Options;
import java.io.IOException;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

public class DelegatingReactorClient implements ReactiveClient {

  private final Client delegate;
  private final Options options;

  DelegatingReactorClient(Client delegate, Options options) {
    this.delegate = delegate;
    this.options = options;
  }

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
