package dagger.internal.codegen.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class ImmutableSortedSet<T> extends ImmutableSet<T> {

  private static final ImmutableSortedSet<?> EMPTY = new ImmutableSortedSet<>(Set.of());

  private ImmutableSortedSet(Set<T> delegate) {
    super(delegate);
  }

  public static <E extends Comparable<E>> Builder<E> naturalOrder() {
    return new Builder<>(Comparator.<E>naturalOrder());
  }

  public static final class Builder<E> {
    private final Set<E> delegate;

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
