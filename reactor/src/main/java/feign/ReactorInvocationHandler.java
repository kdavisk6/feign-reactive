package feign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class ReactorInvocationHandler implements InvocationHandler {

  private final Target<?> target;

  public ReactorInvocationHandler(Target<?> target) {
    this.target = target;
  }


  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {



    return null;
  }
}
