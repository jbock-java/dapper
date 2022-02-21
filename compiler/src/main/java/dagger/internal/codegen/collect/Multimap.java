package dagger.internal.codegen.collect;

import java.util.Collection;
import java.util.Map;

public interface Multimap<K, V> {

  boolean containsKey(K key);

  Collection<V> get(K key);

  interface Builder<X, Y> {
    Builder<X, Y> put(X x, Y y);

    ListMultimap<X, Y> build();
  }

  static <X, Y> Builder<X, Y> builder() {
    return new ListMultimap.Builder<>();
  }

  boolean isEmpty();

  Collection<Map.Entry<K, V>> entries();

  Map<K, ? extends Collection<V>> asMap();
}
