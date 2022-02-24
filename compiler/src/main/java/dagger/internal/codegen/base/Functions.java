package dagger.internal.codegen.base;

import java.util.function.Function;

public class Functions {

  public static <E> Function<Object, E> constant(E value) {
    return x -> value;
  }
}
