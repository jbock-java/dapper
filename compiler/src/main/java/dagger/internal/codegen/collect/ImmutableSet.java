package dagger.internal.codegen.collect;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class ImmutableSet<T> extends AbstractSet<T> implements ImmutableCollection<T> {

  private static final ImmutableSet<?> EMPTY = new ImmutableSet<>(Set.of());
  private final Set<T> delegate;

  ImmutableSet(Set<T> delegate) {
    this.delegate = delegate;
  }

  public static <E> ImmutableSet<E> copyOf(E[] elements) {
    return copyOf(Arrays.asList(elements));
  }

  public static <E> ImmutableSet<E> copyOf(Iterable<? extends E> elements) {
    if (elements instanceof ImmutableSet) {
      return (ImmutableSet<E>) elements;
    }
    if (elements instanceof Set) {
      if (((Set<? extends E>) elements).isEmpty()) {
        return of();
      }
      return new ImmutableSet<>((Set<E>) elements);
    }
    if (elements instanceof Collection) {
      if (((Collection<? extends E>) elements).isEmpty()) {
        return of();
      }
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

    public Builder<E> addAll(Collection<? extends E> elements) {
      delegate.addAll(elements);
      return this;
    }


    public ImmutableSet<E> build() {
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
  public final Iterator<T> iterator() {
    return delegate.iterator();
  }

  @Override
  public final int size() {
    return delegate.size();
  }

  public final ImmutableList<T> asList() {
    return ImmutableList.copyOf(delegate);
  }

  public ImmutableSet<T> immutableCopy() {
    return this;
  }
}
