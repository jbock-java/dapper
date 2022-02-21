package dagger.internal.codegen.collect;

import dagger.internal.codegen.extension.DaggerStreams;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class FluentIterable<E> {

  private Stream<E> delegate;

  public FluentIterable(Stream<E> delegate) {
    this.delegate = delegate;
  }

  public static <T> FluentIterable<T> from(Iterable<T> iterable) {
    return new FluentIterable<>(StreamSupport.stream(iterable.spliterator(), false));
  }

  public FluentIterable<E> filter(Predicate<E> p) {
    delegate = delegate.filter(p);
    return this;
  }

  public ImmutableSet<E> toSet() {
    return delegate.collect(DaggerStreams.toImmutableSet());
  }
}
