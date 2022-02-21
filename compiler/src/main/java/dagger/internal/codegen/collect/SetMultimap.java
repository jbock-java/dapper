package dagger.internal.codegen.collect;

import dagger.internal.codegen.base.Util;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class SetMultimap<K, V> implements ImmutableMultimap<K, V> {

  private final Map<K, Set<V>> map;

  public SetMultimap() {
    this(new LinkedHashMap<>());
  }

  public SetMultimap(Map<K, Set<V>> map) {
    this.map = map;
  }

  public void put(K key, V value) {
    map.merge(key, Set.of(value), Util::mutableUnion);
  }

  public void putAll(K key, Iterable<? extends V> values) {
    for (V value : values) {
      put(key, value);
    }
  }

  public Map<K, Set<V>> asMap() {
    return map;
  }

  public ImmutableSet<K> keySet() {
    return ImmutableSet.copyOf(map.keySet());
  }

  public Collection<V> values() {
    return map.values().stream().flatMap(Set::stream).collect(Collectors.toList());
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
}
