package feign;

import org.reactivestreams.Publisher;

/**
 * A ReactiveClient responsible for submitting {@link Request}s.
 */
@FunctionalInterface
public interface ReactiveClient {

  /**
   * Executes the published {@link Request}s.  Implementations are responsible for subscribing
   * to the {@link Publisher} and managing read and write backpressure.
   *
   * @param requests {@link Publisher} to subscribe to.
   * @return a {@link Publisher} containing the {@link Response}s from the published
   * {@link Request}s
   */
  Publisher<Response> execute(Publisher<Request> requests);

}
