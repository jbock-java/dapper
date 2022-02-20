package dagger.internal.codegen.base;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Sets {

  public static <E> Set<E> newHashSet() {
    return new HashSet<>();
  }

  public static <E> Set<E> difference(Set<E> set1, Set<E> set2) {
    return set1.stream()
        .filter(e -> !set2.contains(e))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public static <E> Set<E> intersection(Set<E> set1, Set<E> set2) {
    return set1.stream()
        .filter(set2::contains)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public static <E> Set<E> union(Set<E> set1, Set<E> set2) {
    Set<E> result = new LinkedHashSet<>(Math.max(4, (int) (1.5 * (set1.size() + set2.size()))));
    result.addAll(set1);
    result.addAll(set2);
    return result;
  }
}
