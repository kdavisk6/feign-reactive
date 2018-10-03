package feign;

import org.reactivestreams.Publisher;

/**
 * A ReactiveClient responsible for submitting {@link Request}s.
 */
@FunctionalInterface
public interface ReactiveClient {

  /**
   * <p>
   *  Executes the published {@link Request}s.  Implementations are responsible for subscribing
   *  to the {@link Publisher} and managing read and write backpressure.
   * </p>
   * <p>
   *  Clients are expected to treat all response status codes outside of the 2xx range as
   *  errors and publish the response as such.
   * </p>
   * <p>
   *   The content of the Response body are expected to be read fully before returning.  Streaming
   *   of the body is not supported, as it is counter to reactive streams desire to allow the
   *   Subscriber to manage back-pressure.
   * </p>
   *
   * @param requests {@link Publisher} to subscribe to.
   * @return a {@link Publisher} containing the {@link Response}s from the published
   * {@link Request}s
   */
  Publisher<Response> execute(Publisher<Request> requests);

}
