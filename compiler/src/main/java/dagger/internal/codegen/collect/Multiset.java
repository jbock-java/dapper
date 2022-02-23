package dagger.internal.codegen.collect;

import java.util.LinkedHashMap;

public class Multiset<E> {

  private final LinkedHashMap<E, Integer> map;

  public Multiset() {
    this(new LinkedHashMap<>());
  }

  public Multiset(LinkedHashMap<E, Integer> map) {
    this.map = map;
  }

  public void add(E element) {
    add(element, 1);
  }

  public void add(E element, int occurrences) {
    map.merge(element, occurrences, Integer::sum);
  }

  public int count(E element) {
    return map.getOrDefault(element, 0);
  }

  public int size() {
    return map.values().stream().mapToInt(Integer::intValue).sum();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Multiset<?> multiset = (Multiset<?>) o;
    return map.equals(multiset.map);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  @Override
  public final String toString() {
    return map.toString();
  }
}
