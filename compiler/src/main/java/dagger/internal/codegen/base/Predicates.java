package dagger.internal.codegen.base;

import java.util.Collection;
import java.util.function.Predicate;

public class Predicates {

  public static <T> Predicate<T> in(Collection<? extends T> target) {
    return target::contains;
  }
}
