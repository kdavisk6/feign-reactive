package feign;

import feign.util.MultiValueMap;
import java.nio.charset.Charset;
import org.reactivestreams.Publisher;

/**
 * A single HttpRequest specification.
 */
public interface Request {

  String url();

  HttpMethod method();

  MultiValueMap<String, String> headers();

  Charset charset();

  Publisher body();

}
