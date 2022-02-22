package dagger.internal.codegen.collect;

import java.util.function.Function;
import java.util.function.Predicate;

public class Multimaps {

  public static <K, V> ImmutableListMultimap<K, V> index(Iterable<V> values, Function<? super V, K> keyFunction) {
    ImmutableListMultimap.Builder<K, V> result = ImmutableListMultimap.builder();
    for (V v : values) {
      result.put(keyFunction.apply(v), v);
    }
    return result.build();
  }

  public static <K, V> ImmutableSetMultimap<K, V> filterKeys(
      ImmutableSetMultimap<K, V> unfiltered, Predicate<? super K> keyPredicate) {
    return unfiltered.filterKeys(keyPredicate);
  }
}
