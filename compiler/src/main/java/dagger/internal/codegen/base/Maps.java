package dagger.internal.codegen.base;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Maps {

  public static <K, V1, V2>
  Map<K, V2> transformValues(Map<K, V1> fromMap, Function<? super V1, V2> function) {
    LinkedHashMap<K, V2> result = new LinkedHashMap<>(Math.max(5, (int) (fromMap.size() * 1.5)));
    fromMap.forEach((k, v) -> result.put(k, function.apply(v)));
    return result;
  }

  public static <K, V> ImmutableMap<K, V> toMap(
      Collection<K> set, Function<? super K, V> function) {
    LinkedHashMap<K, V> result = new LinkedHashMap<>(Math.max(4, (int) (1.5 * set.size())));
    for (K k : set) {
      result.put(k, function.apply(k));
    }
    return ImmutableMap.copyOf(result);
  }

  public static <K, V>
  ImmutableMap<K, V> filterKeys(Map<K, V> fromMap, Predicate<K> predicate) {
    LinkedHashMap<K, V> result = new LinkedHashMap<>();
    fromMap.forEach((key, value) -> {
      if (!predicate.test(key)) {
        return;
      }
      result.put(key, value);
    });
    return ImmutableMap.copyOf(result);
  }

}
