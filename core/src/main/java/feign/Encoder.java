package feign;

import org.reactivestreams.Publisher;

@FunctionalInterface
public interface Encoder {

  /**
   * Encodes the {@link Request} provided.
   *
   * @param request publisher to subscribe to.
   * @return a Publishers containing the encoded requests.
   */
   Publisher<Request> encode(Publisher<Request> request);
}
