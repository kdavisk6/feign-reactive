package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

public class ReactorInvocationHandler implements InvocationHandler {

  private final Target<?> target;
  private final Map<Method, MethodHandler> methodHandlerMap;


  ReactorInvocationHandler(Target<?> target,
                           Map<Method, MethodHandler> methodHandlerMap) {
    this.target = target;
    this.methodHandlerMap = methodHandlerMap;
  }


  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    MethodHandler methodHandler = this.methodHandlerMap.get(method);
    if (methodHandler != null) {
      /* invoke the method handler */
      return methodHandler.invoke(args);
    } else {
      /* invoke on the proxy */
      return method.invoke(proxy, args);
    }
  }

}
