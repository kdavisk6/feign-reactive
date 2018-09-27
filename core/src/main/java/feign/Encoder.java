package feign;

import java.nio.ByteBuffer;
import org.reactivestreams.Publisher;

@FunctionalInterface
public interface Encoder {

  /**
   * Encodes the entities provided.
   *
   * @param entities publisher to subscribe to.
   * @param <T> of objects on the incoming publisher.
   * @return a Publishers containing the encoded objects, as a ByteBuffer.
   */
  <T> Publisher<ByteBuffer> encode(Publisher<T> entities);
}
