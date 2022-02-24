package dagger.internal.codegen.collect;

public class MultimapBuilder<K> {

  public static <K> MultimapBuilder<K> enumKeys(Class<K> keyClass) {
    return new MultimapBuilder<>();
  }

  public static <K> MultimapBuilder<K> hashKeys() {
    return new MultimapBuilder<>();
  }

  public MultimapBuilder<K> arrayListValues() {
    return this;
  }

  public <V> ListMultimap<K, V> build() {
    return new ImmutableListMultimap<>();
  }

  public <K, V> ListMultimap<K, V> build(Multimap<? extends K, ? extends V> multimap) {
    ImmutableListMultimap.Builder<K, V> result = ImmutableListMultimap.builder();
    result.putAll(multimap);
    return result.build();
  }
}
