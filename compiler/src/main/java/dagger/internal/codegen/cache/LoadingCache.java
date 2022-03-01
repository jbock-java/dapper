package dagger.internal.codegen.cache;

import java.util.HashMap;
import java.util.function.Function;

public class LoadingCache<K, V> implements Function<K, V> {

  private final Function<K, V> computingFunction;
  private final HashMap<K, V> map = new HashMap<>();

  LoadingCache(Function<K, V> computingFunction) {
    this.computingFunction = computingFunction;
  }

  @Override
  public V apply(K k) {
    return map.computeIfAbsent(k, computingFunction);
  }
}
