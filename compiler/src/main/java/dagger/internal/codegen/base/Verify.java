package dagger.internal.codegen.base;

public class Verify {

  public static void verify(boolean expression, String errorMessageTemplate, Object p1) {
    if (!expression) {
      throw new IllegalArgumentException(String.format(errorMessageTemplate, p1));
    }
  }

  public static void verify(boolean expression, String errorMessageTemplate, Object p1, Object p2) {
    if (!expression) {
      throw new IllegalArgumentException(String.format(errorMessageTemplate, p1, p2));
    }
  }

  public static void verify(
      boolean expression, String errorMessageTemplate, Object p1, Object p2, Object p3) {
    if (!expression) {
      throw new IllegalArgumentException(String.format(errorMessageTemplate, p1, p2, p3));
    }
  }

  public static void verify(boolean expression) {
    if (!expression) {
      throw new IllegalArgumentException();
    }
  }

  public static void verify(boolean expression, String errorMessage) {
    if (!expression) {
      throw new IllegalArgumentException(errorMessage);
    }
  }

  public static void verifyNotNull(Object o) {
    if (o == null) {
      throw new NullPointerException();
    }
  }
}
