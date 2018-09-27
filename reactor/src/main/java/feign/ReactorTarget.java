package feign;

import feign.util.Assert;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReactorTarget<T> implements Target<T> {

  private final Map<Method, ReactorMethod> methodMap = new LinkedHashMap<>();
  private final String name;
  private final String host;
  private final Class<T> type;

  ReactorTarget(Class<T> type, String host, String name) {
    Assert.isNotEmpty(name, "name is required");
    Assert.isNotEmpty(host, "host is required");
    Assert.isNotNull(type, "a type is required");
    this.name = name;
    this.host = host;
    this.type = type;
  }

  ReactorTarget(Class<T> type, String host) {
    this(type, host, host);
  }

  @Override
  public String name() {
    return this.name;
  }

  @Override
  public String host() {
    return this.host;
  }

  @Override
  public Class<T> type() {
    return this.type;
  }

  @Override
  public TargetMethod method(Method method) {
    return this.methodMap.get(method);
  }

  void registerMethod(Method method, ReactorMethod targetMethod) {
    if (this.methodMap.containsKey(method)) {
      throw new IllegalStateException(
          "Methods can only be registered once.  Method [" + method.getName()
              + "] has already been targeted.");
    }
    this.methodMap.put(method, targetMethod);
  }
}
