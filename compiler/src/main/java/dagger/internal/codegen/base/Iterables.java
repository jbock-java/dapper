package dagger.internal.codegen.base;

import java.util.ArrayList;

public class Iterables {

  public static <E> Iterable<E> concat(Iterable<? extends E> e) {
    return (Iterable<E>) e;
  }

  public static <E> Iterable<E> concat(Iterable<? extends E>... iterables) {
    ArrayList<E> result = new ArrayList<>();
    for (Iterable<? extends E> iterable : iterables) {
      iterable.forEach(result::add);
    }
    return result;
  }
}
