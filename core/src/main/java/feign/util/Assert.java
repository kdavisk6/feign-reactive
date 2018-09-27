package feign.util;

public final class Assert {

  public static void isNotNull(Object obj, String message) {
    if (obj == null) {
      throw new IllegalArgumentException(message);
    }
  }

  public static void isNotEmpty(String value, String message) {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
  }
}
