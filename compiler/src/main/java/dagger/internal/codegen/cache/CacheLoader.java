package dagger.internal.codegen.cache;

import java.util.function.Function;

public class CacheLoader<K, V> {

  private final Function<K, V> computingFunction;

  private CacheLoader(Function<K, V> computingFunction) {
    this.computingFunction = computingFunction;
  }

  public static <K, V> CacheLoader<K, V> from(Function<K, V> function) {
    return new CacheLoader<>(function);
  }

  Function<K, V> computingFunction() {
    return computingFunction;
  }
}
