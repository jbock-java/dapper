package dagger.internal.codegen.base;

import java.util.HashSet;
import java.util.Set;

public class Sets {

  public static <E> Set<E> newHashSet() {
    return new HashSet<>();
  }
}
