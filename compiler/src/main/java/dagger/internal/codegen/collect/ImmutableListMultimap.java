package dagger.internal.codegen.collect;

import java.util.List;
import java.util.Map;

public class ImmutableListMultimap<K, V> extends ListMultimap<K, V> {

  ImmutableListMultimap() {
  }

  ImmutableListMultimap(Map<K, List<V>> map) {
    super(map);
  }

  public static <X, Y> Builder<X, Y> builder() {
    return new Builder<>();
  }

  public static final class Builder<X, Y> implements ImmutableMultimap.Builder<X, Y> {

    private final ImmutableListMultimap<X, Y> map = new ImmutableListMultimap<>();

    @Override
    public ImmutableMultimap.Builder<X, Y> put(X x, Y y) {
      map.put(x, y);
      return this;
    }

    @Override
    public ImmutableListMultimap<X, Y> build() {
      return map;
    }

    public void putAll(Multimap<? extends X, ? extends Y> multimap) {
      multimap.entries().stream().forEach(e -> put(e.getKey(), e.getValue()));
    }
  }

  public static <X, Y> ImmutableListMultimap<X, Y> copyOf(Map<X, List<Y>> map) {
    return new ImmutableListMultimap<>(map);
  }
}
