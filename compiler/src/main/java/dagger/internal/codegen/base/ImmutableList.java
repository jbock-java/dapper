package dagger.internal.codegen.base;

import java.util.List;

public class ImmutableList {

  public static <E> List<E> copyOf(Iterable<? extends E> elements) {
    if (elements instanceof List) {
      return (List<E>) elements;
    }
    return Util.listOf(elements);
  }
}
