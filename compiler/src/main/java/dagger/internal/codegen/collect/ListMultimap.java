package dagger.internal.codegen.collect;

import dagger.internal.codegen.base.Util;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ListMultimap<K, V> implements ImmutableMultimap<K, V> {

  private final Map<K, List<V>> map = new LinkedHashMap<>();

  public void put(K key, V value) {
    map.merge(key, List.of(value), Util::mutableConcat);
  }

  public Map<K, Collection<V>> asMap() {
    return (Map<K, Collection<V>>) (Map<K, ?>) map;
  }

  public Multiset<K> keys() {
    Multiset<K> result = new Multiset<>();
    map.forEach((k, values) -> result.add(k, values.size()));
    return result;
  }

  public static final class Builder<X, Y> implements ImmutableMultimap.Builder<X, Y> {

    private final ListMultimap<X, Y> map = new ImmutableListMultimap<>();

    @Override
    public ImmutableMultimap.Builder<X, Y> put(X x, Y y) {
      map.put(x, y);
      return this;
    }

    public ListMultimap<X, Y> build() {
      return map;
    }
  }

  @Override
  public ImmutableList<V> get(K key) {
    return ImmutableList.copyOf(map.getOrDefault(key, List.of()));
  }

  @Override
  public boolean isEmpty() {
    if (map.isEmpty()) {
      return true;
    }
    return map.values().stream().allMatch(List::isEmpty);
  }

  @Override
  public final Collection<Map.Entry<K, V>> entries() {
    return asMap().entrySet().stream().<Map.Entry<K, V>>flatMap(entry -> {
      if (entry.getValue().isEmpty()) {
        return Stream.of();
      }
      return entry.getValue().stream().map(v -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), v));
    }).collect(Collectors.toList());
  }

  public ListMultimap<K, V> build() {
    return this;
  }

  @Override
  public boolean containsKey(K key) {
    return map.containsKey(key);
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ListMultimap<?, ?> that = (ListMultimap<?, ?>) o;
    return map.equals(that.map);
  }

  @Override
  public final int hashCode() {
    return map.hashCode();
  }

  @Override
  public final String toString() {
    return map.toString();
  }
}
