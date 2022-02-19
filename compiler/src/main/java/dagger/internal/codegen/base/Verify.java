package dagger.internal.codegen.base;

public class Verify {

  public static void verify(boolean expression, String errorMessageTemplate, Object p1) {
    if (!expression) {
      throw new IllegalArgumentException(String.format(errorMessageTemplate, p1));
    }
  }
}
