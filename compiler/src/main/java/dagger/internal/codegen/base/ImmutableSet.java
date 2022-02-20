package dagger.internal.codegen.base;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class ImmutableSet {

  public static <E> Set<E> copyOf(Collection<? extends E> elements) {
    if (elements instanceof Set) {
      return (Set<E>) elements;
    }
    return new LinkedHashSet<>(elements);
  }
}
