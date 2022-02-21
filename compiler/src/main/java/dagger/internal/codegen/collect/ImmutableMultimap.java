package dagger.internal.codegen.collect;

public interface ImmutableMultimap<K, V> extends Multimap<K, V> {

  interface Builder<X, Y> extends Multimap.Builder<X, Y> {
    Builder<X, Y> put(X x, Y y);

    ListMultimap<X, Y> build();
  }

  static <X, Y> Builder<X, Y> builder() {
    return new ListMultimap.Builder<>();
  }
}
