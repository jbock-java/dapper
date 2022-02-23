package dagger.internal.codegen.collect;

import dagger.internal.codegen.extension.DaggerStreams;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class ImmutableSetMultimap<K, V> extends SetMultimap<K, V> {

  private static final ImmutableSetMultimap<?, ?> EMPTY = new ImmutableSetMultimap<>(Map.of());

  public ImmutableSetMultimap() {
  }

  public ImmutableSetMultimap(Map<K, Set<V>> map) {
    super(map);
  }

  public static <X, Y> Builder<X, Y> builder() {
    return new Builder<>();
  }

  public static final class Builder<X, Y> {
    private final ImmutableSetMultimap<X, Y> map = new ImmutableSetMultimap<>();

    public Builder<X, Y> put(X x, Y y) {
      map.put(x, y);
      return this;
    }

    public Builder<X, Y> putAll(X x, Iterable<Y> values) {
      for (Y y : values) {
        map.put(x, y);
      }
      return this;
    }

    public ImmutableSetMultimap<X, Y> build() {
      return map;
    }
  }

  public static <K, V> ImmutableSetMultimap<K, V> copyOf(Map<K, Set<V>> map) {
    return new ImmutableSetMultimap<>(map);
  }

  public static <K, V> ImmutableSetMultimap<K, V> copyOf(
      Multimap<? extends K, ? extends V> multimap) {
    if (multimap.isEmpty()) {
      return of();
    }

    if (multimap instanceof ImmutableSetMultimap) {
      @SuppressWarnings("unchecked") // safe since multimap is not writable
      ImmutableSetMultimap<K, V> kvMultimap = (ImmutableSetMultimap<K, V>) multimap;
      return kvMultimap;
    }

    ImmutableSetMultimap<K, V> result = new ImmutableSetMultimap<>();
    multimap.entries().forEach(e -> result.put(e.getKey(), e.getValue()));
    return result;
  }

  @SuppressWarnings("unchecked")
  public static <X, Y> ImmutableSetMultimap<X, Y> of() {
    return (ImmutableSetMultimap<X, Y>) EMPTY;
  }

  public ImmutableSetMultimap<V, K> inverse() {
    Map<K, Collection<V>> source = asMap();
    ImmutableSetMultimap<V, K> result = new ImmutableSetMultimap<>();
    source.forEach((k, values) -> values.forEach(v -> result.put(v, k)));
    return result;
  }

  ImmutableSetMultimap<K, V> filterKeys(Predicate<? super K> predicate) {
    return new ImmutableSetMultimap<>(asMap().entrySet().stream()
        .filter(e -> predicate.test(e.getKey()))
        .collect(DaggerStreams.toImmutableMap(
            Map.Entry::getKey,
            e -> ImmutableSet.copyOf(e.getValue()))));
  }
}
