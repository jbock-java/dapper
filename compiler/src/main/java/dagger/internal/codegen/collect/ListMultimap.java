package dagger.internal.codegen.collect;

import dagger.internal.codegen.base.Util;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ListMultimap<K, V> {

  private final Map<K, List<V>> map = new LinkedHashMap<>();

  public void put(K key, V value) {
    map.merge(key, List.of(value), Util::mutableConcat);
  }

  public Map<K, List<V>> asMap() {
    return map;
  }

  public Multiset<K> keys() {
    Multiset<K> result = new Multiset<>();
    map.forEach((k, values) -> result.add(k, values.size()));
    return result;
  }

  public List<V> get(K key) {
    return map.getOrDefault(key, List.of());
  }

  public ListMultimap<K, V> build() {
    return this;
  }

  public boolean containsKey(K key) {
    return map.containsKey(key);
  }
}
