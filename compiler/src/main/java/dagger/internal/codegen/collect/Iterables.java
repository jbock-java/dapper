package dagger.internal.codegen.collect;

import dagger.internal.codegen.extension.DaggerStreams;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
    return getOnlyElement(iterable.iterator());
  }

  public static <E> E getOnlyElement(Iterable<? extends E> iterable, E defaultValue) {
    if (!iterable.iterator().hasNext()) {
      return defaultValue;
    }
    return getOnlyElement(iterable);
  }

  private static <T> T getOnlyElement(Iterator<T> it) {
    if (!it.hasNext()) {
      throw new IllegalArgumentException("Expecting exactly one element but was empty");
    }
    T result = it.next();
    if (it.hasNext()) {
      throw new IllegalArgumentException("Expecting exactly one element but found more");
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  public static <T> T[] toArray(Collection<? extends T> iterable, Class<T> type) {
    T[] o = (T[]) Array.newInstance(type, 0);
    return iterable.toArray(o);
  }

  public static <T> T find(
      Iterable<T> iterable, Predicate<? super T> predicate) {
    return Iterators.find(iterable.iterator(), predicate);
  }

  public static <F, T> Iterable<T> transform(
      Iterable<F> fromIterable, Function<? super F, ? extends T> function) {
    return DaggerStreams.stream(fromIterable).map(function).collect(Collectors.toList());
  }

  public static <T> Iterable<T> filter(
      Iterable<T> unfiltered, Predicate<? super T> retainIfTrue) {
    return DaggerStreams.stream(unfiltered).filter(retainIfTrue).collect(Collectors.toList());
  }

  public static <T> Iterable<T> consumingIterable(Iterable<T> iterable) {
    return () -> Iterators.consumingIterator(iterable.iterator());
  }

  public static <T> Iterable<T> limit(Iterable<T> iterable, int limitSize) {
    return () -> Iterators.limit(iterable.iterator(), limitSize);
  }

  public static <T> Iterable<T> skip(Iterable<T> iterable, int numberToSkip) {
    return () -> {
      if (iterable instanceof List) {
        List<T> list = (List<T>) iterable;
        int toSkip = Math.min(list.size(), numberToSkip);
        return list.subList(toSkip, list.size()).iterator();
      }
      Iterator<T> iterator = iterable.iterator();
      Iterators.advance(iterator, numberToSkip);

      /* return an iterator that does not support remove */
      return new Iterator<T>() {
        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public T next() {
          return iterator.next();
        }
      };
    };
  }

  public static <T> int indexOf(Iterable<T> iterable, Predicate<? super T> predicate) {
    return Iterators.indexOf(iterable.iterator(), predicate);
  }

  public static <T> T get(Iterable<T> iterable, int position) {
    return (iterable instanceof List)
        ? ((List<T>) iterable).get(position)
        : Iterators.get(iterable.iterator(), position);
  }
}
