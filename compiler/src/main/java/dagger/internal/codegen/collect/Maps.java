package dagger.internal.codegen.collect;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Maps {

  private static final int MAX_POWER_OF_TWO = 1 << (Integer.SIZE - 2);

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

  public static <K, V> ImmutableMap<K, V> uniqueIndex(
      Collection<V> values, Function<? super V, K> keyFunction) {
    LinkedHashMap<K, V> result = new LinkedHashMap<>((int) (values.size() * 1.4));
    for (V value : values) {
      K key = keyFunction.apply(value);
      V previous = result.put(key, value);
      if (previous != null) {
        throw new IllegalArgumentException("Duplicate key: " + key);
      }
    }
    return new ImmutableMap<>(result);
  }

  public static <K, V> HashMap<K, V> newHashMapWithExpectedSize(int expectedSize) {
    return new HashMap<>(capacity(expectedSize));
  }

  static int capacity(int expectedSize) {
    if (expectedSize < 3) {
      return expectedSize + 1;
    }
    if (expectedSize < MAX_POWER_OF_TWO) {
      // This is the calculation used in JDK8 to resize when a putAll
      // happens; it seems to be the most conservative calculation we
      // can make.  0.75 is the default load factor.
      return (int) ((float) expectedSize / 0.75F + 1.0F);
    }
    return Integer.MAX_VALUE; // any large value
  }
}
