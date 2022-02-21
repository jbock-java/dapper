package dagger.internal.codegen.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

public class ImmutableSortedSet<T> extends ImmutableSet<T> {

  private static final ImmutableSortedSet<?> EMPTY = new ImmutableSortedSet<>(Collections.emptySortedSet());

  private ImmutableSortedSet(SortedSet<T> delegate) {
    super(delegate);
  }

  public static <E extends Comparable<E>> Builder<E> naturalOrder() {
    return new Builder<>(Comparator.<E>naturalOrder());
  }

  public static <E> ImmutableSortedSet<E> copyOf(
      Comparator<E> comparator,
      Iterable<? extends E> elements) {
    if (elements instanceof ImmutableSortedSet) {
      return (ImmutableSortedSet<E>) elements;
    }
    TreeSet<E> result = new TreeSet<>(comparator);
    elements.forEach(result::add);
    return new ImmutableSortedSet<>(result);
  }

  public static final class Builder<E> {
    private final TreeSet<E> delegate;

    public Builder(Comparator<E> comparator) {
      this.delegate = new TreeSet<>(comparator);
    }

    public Builder<E> add(E element) {
      delegate.add(element);
      return this;
    }

    public Builder<E> addAll(Collection<E> elements) {
      delegate.addAll(elements);
      return this;
    }

    public ImmutableSortedSet<E> build() {
      return new ImmutableSortedSet<>(delegate);
    }
  }

  public static <E> ImmutableSortedSet<E> of() {
    return (ImmutableSortedSet<E>) EMPTY;
  }
}
