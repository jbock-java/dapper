package dagger.internal.codegen.base;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class SetMultimap<K, V> {

  private final Map<K, Set<V>> map = new LinkedHashMap<>();

  public void put(K key, V value) {
    map.merge(key, Set.of(value), Util::mutableUnion);
  }

  public Map<K, Set<V>> asMap() {
    return map;
  }
}
