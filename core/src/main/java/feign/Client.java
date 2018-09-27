package feign;

import org.reactivestreams.Publisher;

/**
 * A Client responsible for submitting {@link Request}s.
 */
public interface Client {

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
