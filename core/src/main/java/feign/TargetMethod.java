package feign;

import org.reactivestreams.Publisher;

public interface TargetMethod {

  Publisher<Request> resolve(Object[] args);

}
