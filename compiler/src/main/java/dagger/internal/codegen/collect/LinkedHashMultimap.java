package dagger.internal.codegen.collect;

public class LinkedHashMultimap {

  public static <K, V> SetMultimap<K, V> create() {
    return new ImmutableSetMultimap<>();
  }
}
