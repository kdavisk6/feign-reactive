package feign;

import java.lang.reflect.Type;
import org.reactivestreams.Publisher;

abstract class TargetMethod {

  private transient Type returnType;


  abstract Publisher<Request> resolve(Object[] args);

}
