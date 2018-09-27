package feign;

import java.lang.reflect.InvocationHandler;

public class ReactorInvocationHandlerFactory implements InvocationHandlerFactory {

  @Override
  public InvocationHandler create(Target target) {
    return new ReactorInvocationHandler(target);
  }
}
