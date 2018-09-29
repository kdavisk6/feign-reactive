package feign;

import org.reactivestreams.Publisher;

/**
 * Converts the incoming response information into the desired type.  Each incoming {@link Response}
 * will produce a new instance of the desired type and emit via the returned {@link Publisher}.
 */
@FunctionalInterface
public interface Decoder {

  /**
   * Decode the incoming {@link Response} into the desired Type.
   * @param response to decode.
   * @param <T> type of the object to decode the {@link Response} body into.
   * @return a Publisher containing the desired types.
   */
  <T> Publisher<T> decode(Publisher<Response> response);

}
