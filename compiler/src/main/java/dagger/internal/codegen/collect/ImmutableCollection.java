package dagger.internal.codegen.collect;

import java.util.Collection;

public interface ImmutableCollection<E> extends Collection<E> {
  ImmutableList<E> asList();
}
