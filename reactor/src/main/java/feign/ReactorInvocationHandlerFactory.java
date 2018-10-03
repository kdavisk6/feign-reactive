package feign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

public class ReactorInvocationHandlerFactory implements InvocationHandlerFactory {


  @Override
  public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
    return null;
  }
}
