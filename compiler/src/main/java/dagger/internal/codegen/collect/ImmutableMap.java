package dagger.internal.codegen.collect;

import dagger.internal.codegen.base.Util;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ImmutableMap<X, Y> extends AbstractMap<X, Y> {

  private static final ImmutableMap<?, ?> EMPTY = new ImmutableMap<>(Map.of());

  private final Map<X, Y> delegate;

  ImmutableMap(Map<X, Y> delegate) {
    this.delegate = delegate;
  }

  public static <K, V> ImmutableMap<K, V> copyOf(Map<K, V> map) {
    if (map instanceof ImmutableMap) {
      return (ImmutableMap<K, V>) map;
    }
    return new ImmutableMap<>(map);
  }

  public static <X, Y> Builder<X, Y> builder() {
    return new Builder<>();
  }

  public static class Builder<X, Y> {
    private final Map<X, Y> delegate = new LinkedHashMap<>();

    Map<X, Y> delegate() {
      return delegate;
    }

    public Builder<X, Y> put(X key, Y value) {
      delegate.put(key, value);
      return this;
    }

    public Builder<X, Y> putAll(Map<? extends X, ? extends Y> map) {
      map.forEach(this::put);
      return this;
    }

    public ImmutableMap<X, Y> build() {
      return new ImmutableMap<>(delegate);
    }
  }

  public static <K, V> ImmutableMap<K, V> of() {
    return (ImmutableMap<K, V>) EMPTY;
  }

  public static <K, V> ImmutableMap<K, V> of(K k1, V v1) {
    return new ImmutableMap<>(Map.of(k1, v1));
  }

  public static <K, V> ImmutableMap<K, V> of(K k1, V v1, K k2, V v2) {
    LinkedHashMap<K, V> result = new LinkedHashMap<>(3);
    result.put(k1, v1);
    result.put(k2, v2);
    return new ImmutableMap<>(result);
  }

  public static <K, V> ImmutableMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
    LinkedHashMap<K, V> result = new LinkedHashMap<>(5);
    result.put(k1, v1);
    result.put(k2, v2);
    result.put(k3, v3);
    return new ImmutableMap<>(result);
  }

  public static <K, V> ImmutableMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    LinkedHashMap<K, V> result = new LinkedHashMap<>(6);
    result.put(k1, v1);
    result.put(k2, v2);
    result.put(k3, v3);
    result.put(k4, v4);
    return new ImmutableMap<>(result);
  }

  public static <K, V> ImmutableMap<K, V> of(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
    LinkedHashMap<K, V> result = new LinkedHashMap<>(8);
    result.put(k1, v1);
    result.put(k2, v2);
    result.put(k3, v3);
    result.put(k4, v4);
    result.put(k5, v5);
    return new ImmutableMap<>(result);
  }

  @Override
  public Set<Entry<X, Y>> entrySet() {
    return delegate.entrySet();
  }

  final Map<X, Y> delegate() {
    return delegate;
  }

  public ImmutableSetMultimap<X, Y> asMultimap() {
    return new ImmutableSetMultimap<>(Util.transformValues(delegate, Set::of));
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ImmutableMap<?, ?> that = (ImmutableMap<?, ?>) o;
    return delegate.equals(that.delegate);
  }

  @Override
  public ImmutableSet<X> keySet() {
    return ImmutableSet.copyOf(super.keySet());
  }

  @Override
  public ImmutableList<Y> values() {
    return ImmutableList.copyOf(super.values());
  }

  @Override
  public final int hashCode() {
    return Objects.hash(delegate);
  }

  @Override
  public final String toString() {
    return delegate.toString();
  }
}
