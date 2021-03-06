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

  public static <T> T find(Iterator<T> iterator, Predicate<? super T> predicate) {
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

  public static <T> Iterator<T> limit(Iterator<T> iterator, int limitSize) {
    return new Iterator<T>() {
      private int count;

      @Override
      public boolean hasNext() {
        return count < limitSize && iterator.hasNext();
      }

      @Override
      public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        count++;
        return iterator.next();
      }

      @Override
      public void remove() {
        iterator.remove();
      }
    };
  }

  public static int advance(Iterator<?> iterator, int numberToAdvance) {
    int i;
    for (i = 0; i < numberToAdvance && iterator.hasNext(); i++) {
      iterator.next();
    }
    return i;
  }

  public static <T> int indexOf(Iterator<T> iterator, Predicate<? super T> predicate) {
    for (int i = 0; iterator.hasNext(); i++) {
      T current = iterator.next();
      if (predicate.test(current)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the number of elements remaining in {@code iterator}. The iterator will be left
   * exhausted: its {@code hasNext()} method will return {@code false}.
   */
  public static int size(Iterator<?> iterator) {
    long count = 0L;
    while (iterator.hasNext()) {
      iterator.next();
      count++;
    }
    return saturatedCast(count);
  }

  private static int saturatedCast(long value) {
    if (value > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    if (value < Integer.MIN_VALUE) {
      return Integer.MIN_VALUE;
    }
    return (int) value;
  }
}
