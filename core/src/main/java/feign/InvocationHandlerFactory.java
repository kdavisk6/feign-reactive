package feign;

import java.lang.reflect.InvocationHandler;

public interface InvocationHandlerFactory {

  InvocationHandler create(Target target);

}
