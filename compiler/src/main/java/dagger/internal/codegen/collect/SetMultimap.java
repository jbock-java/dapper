package dagger.internal.codegen.collect;

import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.extension.DaggerStreams;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class SetMultimap<K, V> implements ImmutableMultimap<K, V> {

  private final Map<K, Set<V>> map;

  public SetMultimap() {
    this(new LinkedHashMap<>());
  }

  public SetMultimap(Map<K, Set<V>> map) {
    this.map = map;
  }

  public boolean put(K key, V value) {
    int oldSize = map.getOrDefault(key, Set.of()).size();
    Set<V> newSet = map.merge(key, Set.of(value), Util::mutableUnion);
    return newSet.size() != oldSize;
  }

  public void putAll(K key, Iterable<? extends V> values) {
    for (V value : values) {
      put(key, value);
    }
  }

  public Map<K, Collection<V>> asMap() {
    return (Map<K, Collection<V>>) (Map<K, ?>) map;
  }

  public ImmutableSet<K> keySet() {
    return ImmutableSet.copyOf(map.keySet());
  }

  public ImmutableCollection<V> values() {
    return map.values().stream().flatMap(Set::stream).collect(DaggerStreams.toImmutableList());
  }

  public SetMultimap<K, V> build() {
    return this;
  }

  @Override
  public ImmutableSet<V> get(K key) {
    return ImmutableSet.copyOf(map.getOrDefault(key, Set.of()));
  }

  @Override
  public boolean containsKey(K key) {
    return map.containsKey(key);
  }

  public boolean isEmpty() {
    if (map.isEmpty()) {
      return true;
    }
    return map.values().stream().allMatch(Set::isEmpty);
  }

  @Override
  public final Collection<Map.Entry<K, V>> entries() {
    return asMap().entrySet().stream()
        .<Map.Entry<K, V>>flatMap(
            entry -> {
              if (entry.getValue().isEmpty()) {
                return Stream.of();
              }
              return entry.getValue().stream()
                  .map(v -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), v));
            })
        .collect(Collectors.toList());
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SetMultimap<?, ?> that = (SetMultimap<?, ?>) o;
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

  @Override
  public final void forEach(BiConsumer<? super K, ? super V> action) {
    asMap()
        .forEach(
            (key, valueCollection) -> valueCollection.forEach(value -> action.accept(key, value)));
  }

  public Set<V> replaceValues(K key, Iterable<? extends V> values) {
    Set<V> result = map.put(key, ImmutableSet.copyOf(values));
    return result == null ? Set.of() : result;
  }
}
