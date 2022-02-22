package dagger.internal.codegen.collect;

import dagger.internal.codegen.extension.DaggerStreams;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class Collections2 {

  private static <F, T> Collection<T> transformSet(
      Set<F> fromCollection, Function<? super F, T> function) {
    return fromCollection.stream().map(function).collect(DaggerStreams.toImmutableSet());
  }

  private static <F, T> Collection<T> transformList(
      List<F> fromCollection, Function<? super F, T> function) {
    return fromCollection.stream().map(function).collect(DaggerStreams.toImmutableList());
  }

  public static <F, T> Collection<T> transform(
      Collection<F> fromCollection, Function<? super F, T> function) {
    if (fromCollection instanceof List) {
      return transformList((List) fromCollection, function);
    }
    if (fromCollection instanceof Set) {
      return transformSet((Set) fromCollection, function);
    }
    throw new IllegalArgumentException("Don't know how to transform " + fromCollection.getClass());
  }
}
