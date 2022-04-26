package dagger.internal.codegen.base;

import static dagger.internal.codegen.base.Preconditions.checkNotNull;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Throwables {

  public static String getStackTraceAsString(Throwable throwable) {
    StringWriter stringWriter = new StringWriter();
    throwable.printStackTrace(new PrintWriter(stringWriter));
    return stringWriter.toString();
  }
  
  public static void propagateIfPossible(Throwable throwable) {
    if (throwable != null) {
      throwIfUnchecked(throwable);
    }
  }
  public static <X extends Throwable> void propagateIfPossible(
      Throwable throwable, Class<X> declaredType) throws X {
    propagateIfInstanceOf(throwable, declaredType);
    propagateIfPossible(throwable);
  }
  
  public static <X extends Throwable> void propagateIfInstanceOf(
      Throwable throwable, Class<X> declaredType) throws X {
    if (throwable != null) {
      throwIfInstanceOf(throwable, declaredType);
    }
  }

  public static <X extends Throwable> void throwIfInstanceOf(
      Throwable throwable, Class<X> declaredType) throws X {
    checkNotNull(throwable);
    if (declaredType.isInstance(throwable)) {
      throw declaredType.cast(throwable);
    }
  }
  
  public static void throwIfUnchecked(Throwable throwable) {
    checkNotNull(throwable);
    if (throwable instanceof RuntimeException) {
      throw (RuntimeException) throwable;
    }
    if (throwable instanceof Error) {
      throw (Error) throwable;
    }
  }}
