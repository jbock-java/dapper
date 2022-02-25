package dagger.internal.codegen.base;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;

public class Predicates {

  public static <T> Predicate<T> in(Collection<? extends T> target) {
    return target::contains;
  }

  public static <T> Predicate<T> equalTo(T target) {
    return o -> Objects.equals(o, target);
  }

  public static <T> Predicate<T> not(Predicate<T> predicate) {
    return t -> !predicate.test(t);
  }

  public static <T> Predicate<T> and(Iterable<? extends Predicate<? super T>> components) {
    return t -> {
      for (Predicate<? super T> p : components) {
        if (!p.test(t)) {
          return false;
        }
      }
      return true;
    };
  }

  @SafeVarargs
  public static <T> Predicate<T> and(Predicate<T>... components) {
    return and(Arrays.asList(components));
  }
}
