package dagger.internal.codegen.base;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class Maps {

  public static <K, V1, V2>
  Map<K, V2> transformValues(Map<K, V1> fromMap, Function<? super V1, V2> function) {
    LinkedHashMap<K, V2> result = new LinkedHashMap<>(Math.max(5, (int) (fromMap.size() * 1.5)));
    fromMap.forEach((k, v) -> result.put(k, function.apply(v)));
    return result;
  }
}
