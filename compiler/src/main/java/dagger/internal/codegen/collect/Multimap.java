package dagger.internal.codegen.collect;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;

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

  Map<K, Collection<V>> asMap();

  void forEach(BiConsumer<? super K, ? super V> action);
}
