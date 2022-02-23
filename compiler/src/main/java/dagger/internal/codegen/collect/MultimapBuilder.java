package dagger.internal.codegen.collect;

public class MultimapBuilder<K> {

  public static <K> MultimapBuilder<K> enumKeys(Class<K> keyClass) {
    return new MultimapBuilder<>();
  }

  public MultimapBuilder<K> arrayListValues() {
    return this;
  }

  public <V> ListMultimap<K, V> build() {
    return new ImmutableListMultimap<>();
  }
}
