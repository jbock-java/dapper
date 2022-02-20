package dagger.internal.codegen.base;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public class ImmutableSortedSet<T> extends AbstractSet<T> {

  private static final ImmutableSortedSet<?> EMPTY = new ImmutableSortedSet<>(Set.of());

  private final Set<T> delegate;

  private ImmutableSortedSet(Set<T> delegate) {
    this.delegate = delegate;
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

  @Override
  public Iterator<T> iterator() {
    return delegate.iterator();
  }

  @Override
  public int size() {
    return delegate.size();
  }

  public ImmutableList<T> asList() {
    return ImmutableList.copyOf(delegate);
  }
}
