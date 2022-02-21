package dagger.internal.codegen.collect;

public class ImmutableListMultimap<K, V> extends ListMultimap<K, V> {

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
  }
}
