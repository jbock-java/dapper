/*
 * Copyright (C) 2013 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen.base;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** General utilities for the annotation processor. */
public final class Util {

  /**
   * A version of {@link Map#computeIfAbsent(Object, Function)} that allows {@code mappingFunction}
   * to update {@code map}.
   */
  public static <K, V> V reentrantComputeIfAbsent(
      Map<K, V> map, K key, Function<? super K, ? extends V> mappingFunction) {
    V value = map.get(key);
    if (value == null) {
      value = mappingFunction.apply(key);
      if (value != null) {
        map.put(key, value);
      }
    }
    return value;
  }

  private Util() {
  }

  public static <E> Set<E> setOf(Iterable<? extends E> elements) {
    LinkedHashSet<E> result = new LinkedHashSet<>();
    for (E element : elements) {
      result.add(element);
    }
    return Collections.unmodifiableSet(result);
  }

  public static <E> Set<E> difference(Set<E> set1, Set<E> set2) {
    LinkedHashSet<E> result = set1.stream()
        .filter(e -> !set2.contains(e))
        .collect(Collectors.toCollection(LinkedHashSet::new));
    return Collections.unmodifiableSet(result);
  }

  public static <E> Set<E> union(Set<E> set1, Set<E> set2) {
    Set<E> result = new LinkedHashSet<>(Math.max(10, (int) (1.5 * (set1.size() + set2.size()))));
    result.addAll(set1);
    result.addAll(set2);
    return result;
  }

  public static <K, V> Map<K, V> toMap(
      Collection<K> set, Function<? super K, V> function) {
    LinkedHashMap<K, V> result = new LinkedHashMap<>(Math.max(10, (int) (set.size() * 1.5)));
    for (K k : set) {
      result.put(k, function.apply(k));
    }
    return result;
  }

  public static <E> E getOnlyElement(Collection<E> collection) {
    if (collection.isEmpty()) {
      throw new IllegalArgumentException("Expecting exactly one element but found empty list");
    }
    if (collection.size() >= 2) {
      throw new IllegalArgumentException("Expecting exactly one element but found: " + collection);
    }
    return collection.iterator().next();
  }

  public static <K, V1, V2>
  Map<K, V2> transformValues(Map<K, V1> fromMap, Function<? super V1, V2> function) {
    LinkedHashMap<K, V2> result = new LinkedHashMap<>(Math.max(10, (int) (fromMap.size() * 1.5)));
    fromMap.forEach((k, v) -> result.put(k, function.apply(v)));
    return result;
  }
}
