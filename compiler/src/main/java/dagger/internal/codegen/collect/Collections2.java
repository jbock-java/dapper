package dagger.internal.codegen.collect;

import dagger.internal.codegen.extension.DaggerStreams;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

public class Collections2 {

  public static <F, T> Collection<T> transform(
      Set<F> fromCollection, Function<? super F, T> function) {
    return fromCollection.stream().map(function).collect(DaggerStreams.toImmutableSet());
  }
}
