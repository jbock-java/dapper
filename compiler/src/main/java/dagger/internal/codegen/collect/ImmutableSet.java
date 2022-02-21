package dagger.internal.codegen.collect;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class ImmutableSet<T> extends AbstractSet<T> implements ImmutableCollection<T> {

  private static final ImmutableSet<?> EMPTY = new ImmutableSet<>(Set.of());
  private final Set<T> delegate;

  private ImmutableSet(Set<T> delegate) {
    this.delegate = delegate;
  }

  public static <E> ImmutableSet<E> copyOf(Iterable<? extends E> elements) {
    if (elements instanceof ImmutableSet) {
      return (ImmutableSet<E>) elements;
    }
    if (elements instanceof Set) {
      return new ImmutableSet<>((Set<E>) elements);
    }
    if (elements instanceof Collection) {
      return new ImmutableSet<>(new LinkedHashSet<>((Collection<? extends E>) elements));
    }
    LinkedHashSet<E> result = new LinkedHashSet<>();
    elements.forEach(result::add);
    return new ImmutableSet<>(result);
  }

  public static <E> Builder<E> builder() {
    return new Builder<>();
  }

  public static final class Builder<E> {
    private final Set<E> delegate = new LinkedHashSet<>();

    public Builder<E> add(E element) {
      delegate.add(element);
      return this;
    }

    public Builder<E> addAll(Collection<E> elements) {
      delegate.addAll(elements);
      return this;
    }


    public ImmutableSet build() {
      return new ImmutableSet(delegate);
    }
  }

  public static <E> ImmutableSet<E> of() {
    return (ImmutableSet<E>) EMPTY;
  }

  public static <E> ImmutableSet<E> of(E e1) {
    return new ImmutableSet<>(Set.of(e1));
  }

  public static <E> ImmutableSet<E> of(E e1, E e2) {
    return new ImmutableSet<>(Set.of(e1, e2));
  }

  public static <E> ImmutableSet<E> of(E e1, E e2, E e3) {
    return new ImmutableSet<>(Set.of(e1, e2, e3));
  }

  public static <E> ImmutableSet<E> of(E e1, E e2, E e3, E e4) {
    return new ImmutableSet<>(Set.of(e1, e2, e3, e4));
  }

  public static <E> ImmutableSet<E> of(E e1, E e2, E e3, E e4, E e5) {
    return new ImmutableSet<>(Set.of(e1, e2, e3, e4, e5));
  }

  public static <E> ImmutableSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6) {
    return new ImmutableSet<>(Set.of(e1, e2, e3, e4, e5, e6));
  }

  public static <E> ImmutableSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
    return new ImmutableSet<>(Set.of(e1, e2, e3, e4, e5, e6, e7));
  }

  public static <E> ImmutableSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
    return new ImmutableSet<>(Set.of(e1, e2, e3, e4, e5, e6, e7, e8));
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
