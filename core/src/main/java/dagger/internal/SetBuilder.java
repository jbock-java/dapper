package dagger.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class SetBuilder<T> {

  private final List<T> contributions;

  private SetBuilder(int estimatedSize) {
    contributions = new ArrayList<>(estimatedSize);
  }

  /**
   * {@code estimatedSize} is the number of bindings which contribute to the set. They may each
   * provide {@code [0..n)} instances to the set. Because the final size is unknown, {@code
   * contributions} are collected in a list and only hashed in {@link #build()}.
   */
  public static <T> SetBuilder<T> newSetBuilder(int estimatedSize) {
    return new SetBuilder<>(estimatedSize);
  }

  public SetBuilder<T> add(T t) {
    contributions.add(t);
    return this;
  }

  public SetBuilder<T> addAll(Collection<? extends T> collection) {
    contributions.addAll(collection);
    return this;
  }

  public Set<T> build() {
    return Set.copyOf(contributions);
  }
}
