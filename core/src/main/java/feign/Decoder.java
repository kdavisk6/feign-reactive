package feign;

import java.nio.ByteBuffer;
import org.reactivestreams.Publisher;

/**
 * Converts the incoming response information into the desired type.  Each incoming ByteBuffer
 * will produce a new instance of the desired type and emit via the returned {@link Publisher}.
 */
@FunctionalInterface
public interface Decoder {

  /**
   * Decode the incoming entities into the desired Type.
   * @param entities to decode.
   * @param <T> type of the object to decode the entities into.
   * @return a Publisher containing the desired types.
   */
  <T> Publisher<T> decode(Publisher<ByteBuffer> entities);

}
