package dagger.internal.codegen.collect;

import dagger.internal.codegen.base.Util;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SetMultimap<K, V> {

  private final LinkedHashMap<K, Set<V>> map;

  public SetMultimap() {
    this(new LinkedHashMap<>());
  }

  public SetMultimap(LinkedHashMap<K, Set<V>> map) {
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

  public Set<K> keySet() {
    return map.keySet();
  }

  public Collection<V> values() {
    return map.values().stream().flatMap(Set::stream).collect(Collectors.toList());
  }

  public SetMultimap<K, V> build() {
    return this;
  }
}
