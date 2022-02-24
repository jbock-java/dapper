package dagger.internal.codegen.collect;

import dagger.internal.codegen.base.Preconditions;
import java.util.LinkedHashMap;
import java.util.Map;

public class ImmutableBiMap<X, Y> extends ImmutableMap<X, Y> {

  ImmutableBiMap(Map<X, Y> delegate) {
    super(delegate);
  }

  public static <A, B> ImmutableBiMap<A, B> copyOf(Map<A, B> map) {
    return new ImmutableBiMap<>(map);
  }

  public static <X, Y> Builder<X, Y> builder() {
    return new Builder<>();
  }

  public static final class Builder<X, Y> extends ImmutableMap.Builder<X, Y> {

    @Override
    public ImmutableBiMap<X, Y> build() {
      return new ImmutableBiMap<>(delegate());
    }
  }

  public ImmutableBiMap<Y, X> inverse() {
    LinkedHashMap<Y, X> result = new LinkedHashMap<>();
    delegate().forEach((k, v) -> Preconditions.checkState(result.put(v, k) == null,
        "Value is reachable from multiple keys: %s", v));
    return new ImmutableBiMap<>(result);
  }
}
