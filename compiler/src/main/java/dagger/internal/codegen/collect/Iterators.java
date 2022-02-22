package dagger.internal.codegen.collect;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

public class Iterators {

  public static <T> T get(Iterator<T> iterator, int position) {
    for (int i = 0; i < position && iterator.hasNext(); i++) {
      iterator.next();
    }
    if (!iterator.hasNext()) {
      throw new IndexOutOfBoundsException("no such element: " + position);
    }
    return iterator.next();
  }

  public static <T> T find(
      Iterator<T> iterator, Predicate<? super T> predicate) {
    while (iterator.hasNext()) {
      T t = iterator.next();
      if (predicate.test(t)) {
        return t;
      }
    }
    throw new NoSuchElementException();
  }

  public static <T> Iterator<T> consumingIterator(Iterator<T> iterator) {
    return new Iterator<T>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        T next = iterator.next();
        iterator.remove();
        return next;
      }
    };
  }
}
