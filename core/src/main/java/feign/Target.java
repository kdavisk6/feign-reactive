package feign;

import java.lang.reflect.Method;

/**
 * The interface target for the generated Proxy.
 *
 * @param <T> interface Type to be proxied.
 */
public interface Target<T> {

  /**
   * Name of the Target.  Used to indentify this target from others being managed.
   *
   * @return the target name.
   */
  String name();

  /**
   * The host to submit all methods in this target to.  Expected values should contain the
   * scheme, host, and port if applicable.
   *
   * @return the host to target.
   */
  String host();

  /**
   * Reference to the Interface Type being targeted.
   *
   * @return Class type reference to the target interface.
   */
  Class<T> type();

  /**
   * The {@link TargetMethod} for the provided interface Method.
   *
   * @param method on the Target interface.
   * @return the {@link TargetMethod} for the method, null if not found.
   */
  TargetMethod method(Method method);
}
