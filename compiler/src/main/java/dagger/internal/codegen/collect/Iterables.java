package dagger.internal.codegen.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

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

  public static <T> boolean any(Iterable<T> iterable, Predicate<? super T> predicate) {
    return StreamSupport.stream(iterable.spliterator(), false).anyMatch(predicate);
  }

  public static <T> T getLast(Iterable<T> iterable) {
    Iterator<T> it = iterable.iterator();
    if (!it.hasNext()) {
      throw new IllegalArgumentException("empty iterable");
    }
    T t = null;
    while (it.hasNext()) {
      t = it.next();
    }
    return t;
  }

  public static <T> T getOnlyElement(Iterable<T> iterable) {
    Iterator<T> it = iterable.iterator();
    if (!it.hasNext()) {
      throw new IllegalArgumentException("Expecting exactly one element but was empty");
    }
    T result = it.next();
    if (it.hasNext()) {
      throw new IllegalArgumentException("Expecting exactly one element but found more");
    }
    return result;
  }
}
