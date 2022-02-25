package dagger.internal.codegen.collect;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class Multimaps {

  public static <K, V> ImmutableListMultimap<K, V> index(
      Iterable<V> values, Function<? super V, K> keyFunction) {
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

  @SuppressWarnings("unchecked")
  // safe by specification of ListMultimap.asMap()
  public static <K, V> Map<K, List<V>> asMap(ListMultimap<K, V> multimap) {
    return (Map<K, List<V>>) (Map<K, ?>) multimap.asMap();
  }

  /**
   * Returns {@link SetMultimap#asMap multimap.asMap()}, with its type corrected from {@code Map<K,
   * Collection<V>>} to {@code Map<K, Set<V>>}.
   *
   * @since 15.0
   */
  @SuppressWarnings("unchecked")
  // safe by specification of SetMultimap.asMap()
  public static <K, V> Map<K, Set<V>> asMap(SetMultimap<K, V> multimap) {
    return (Map<K, Set<V>>) (Map<K, ?>) multimap.asMap();
  }

  public static <K, V> SetMultimap<K, V> filterValues(
      SetMultimap<K, V> unfiltered, Predicate<? super V> valuePredicate) {
    ImmutableSetMultimap.Builder<K, V> builder = ImmutableSetMultimap.builder();
    unfiltered.entries().stream()
        .filter(e -> valuePredicate.test(e.getValue()))
        .forEach(e -> builder.put(e.getKey(), e.getValue()));
    return builder.build();
  }
}
