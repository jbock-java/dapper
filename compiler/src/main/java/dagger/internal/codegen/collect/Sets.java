package dagger.internal.codegen.collect;

import dagger.internal.codegen.extension.DaggerStreams;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Sets {

  public static <E> Set<E> newHashSet() {
    return new HashSet<>();
  }

  public static <E> ImmutableSet<E> difference(Set<E> set1, Set<E> set2) {
    return ImmutableSet.copyOf(set1.stream()
        .filter(e -> !set2.contains(e))
        .collect(Collectors.toCollection(LinkedHashSet::new)));
  }

  public static <E> ImmutableSet<E> intersection(Set<E> set1, Set<E> set2) {
    return ImmutableSet.copyOf(set1.stream()
        .filter(set2::contains)
        .collect(Collectors.toCollection(LinkedHashSet::new)));
  }

  public static <E> ImmutableSet<E> union(Set<E> set1, Set<E> set2) {
    Set<E> result = new LinkedHashSet<>(Math.max(4, (int) (1.5 * (set1.size() + set2.size()))));
    result.addAll(set1);
    result.addAll(set2);
    return ImmutableSet.copyOf(result);
  }

  public static <E> Set<E> filter(
      Set<E> unfiltered, Predicate<? super E> predicate) {
    return unfiltered.stream().filter(predicate).collect(DaggerStreams.toImmutableSet());
  }

  public static <E> Set<E> newHashSetWithExpectedSize(int expectedSize) {
    return new HashSet<E>(Maps.capacity(expectedSize));
  }

  public static <E> Set<E> newLinkedHashSet() {
    return new LinkedHashSet<>();
  }
}
