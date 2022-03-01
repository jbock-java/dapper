package dagger.internal.codegen.collect;

import java.util.LinkedHashMap;
import java.util.Map;

public class HashBasedTable<R, C, V> implements Table<R, C, V> {

  private final Map<R, Map<C, V>> backingMap = new LinkedHashMap<>();

  public static <R, C, V> HashBasedTable<R, C, V> create() {
    return new HashBasedTable<>();
  }

  @Override
  public Map<C, V> row(R rowKey) {
    return backingMap.computeIfAbsent(rowKey, k -> new LinkedHashMap<>());
  }
}
