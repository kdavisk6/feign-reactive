package feign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

public class ReactorInvocationHandlerFactory implements InvocationHandlerFactory {

  /**
   * Create a new Reactor backed {@link InvocationHandler} Proxy.
   *
   * @param target interface to invoke.
   * @param dispatch map containing the {@link Target} method handlers.
   * @return an {@link InvocationHandler} representing the {@link Target} interface
   * {@link java.lang.reflect.Proxy}
   */
  @Override
  public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
    return new ReactorInvocationHandler(target, dispatch);
  }
}
