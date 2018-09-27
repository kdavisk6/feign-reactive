package feign;

import feign.util.MultiValueMap;
import java.nio.ByteBuffer;
import org.reactivestreams.Publisher;

public interface Response {

  int status();

  String reason();

  MultiValueMap<String, String> headers();

  Publisher<ByteBuffer> body();
}
