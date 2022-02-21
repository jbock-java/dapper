package dagger.internal.codegen.collect;

import dagger.internal.codegen.collect.ListMultimap;
import java.util.function.Function;

public class Multimaps {

  public static <K, V> ListMultimap<K, V> index(Iterable<V> values, Function<? super V, K> keyFunction) {
    ListMultimap<K, V> result = new ListMultimap<>();
    for (V v : values) {
      result.put(keyFunction.apply(v), v);
    }
    return result;
  }
}
