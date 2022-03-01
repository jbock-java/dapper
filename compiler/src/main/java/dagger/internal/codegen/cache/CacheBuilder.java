package dagger.internal.codegen.cache;

public class CacheBuilder<K, V> {

  public <K1 extends K, V1 extends V> LoadingCache<K1, V1> build(
      CacheLoader<K1, V1> loader) {
    return new LoadingCache<>(loader.computingFunction());
  }

  public static CacheBuilder<Object, Object> newBuilder() {
    return new CacheBuilder<>();
  }
}
