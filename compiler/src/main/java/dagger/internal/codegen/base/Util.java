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

import dagger.internal.codegen.collect.Iterables;
import dagger.internal.codegen.collect.Maps;
import dagger.internal.codegen.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
    if (elements instanceof Set) {
      @SuppressWarnings("unchecked")
      Set<E> result = (Set<E>) elements;
      return result;
    }
    LinkedHashSet<E> result = new LinkedHashSet<>();
    for (E element : elements) {
      result.add(element);
    }
    return result;
  }

  public static <E> List<E> listOf(Iterable<? extends E> elements) {
    if (elements instanceof List) {
      @SuppressWarnings("unchecked")
      List<E> result = (List<E>) elements;
      return result;
    }
    ArrayList<E> result = new ArrayList<>();
    for (E element : elements) {
      result.add(element);
    }
    return result;
  }

  public static <E> Set<E> difference(Set<E> set1, Set<E> set2) {
    return Sets.difference(set1, set2);
  }

  public static <E> Set<E> intersection(Set<E> set1, Set<E> set2) {
    return Sets.intersection(set1, set2);
  }

  public static <E> Set<E> union(Set<E> set1, Set<E> set2) {
    return Sets.union(set1, set2);
  }

  public static <E> Set<E> mutableUnion(Set<E> set1, Set<E> set2) {
    if (set1 instanceof HashSet) {
      set1.addAll(set2);
      return set1;
    }
    return union(set1, set2);
  }

  public static <E> List<E> concat(List<E> list1, List<E> list2) {
    ArrayList<E> result = new ArrayList<>(list1.size() + list2.size());
    result.addAll(list1);
    result.addAll(list2);
    return result;
  }

  public static <E> List<E> mutableConcat(List<E> list1, List<E> list2) {
    if (list1 instanceof ArrayList) {
      list1.addAll(list2);
      return list1;
    }
    return concat(list1, list2);
  }

  public static <K, V> Map<K, V> toMap(
      Collection<K> set, Function<? super K, V> function) {
    return Maps.toMap(set, function);
  }

  public static <E> E getOnlyElement(Collection<E> collection) {
    return Iterables.getOnlyElement(collection);
  }

  public static <E> E getOnlyElement(Collection<? extends E> collection, E defaultValue) {
    if (collection.isEmpty()) {
      return defaultValue;
    }
    return getOnlyElement(collection);
  }

  public static <K, V1, V2>
  Map<K, V2> transformValues(Map<K, V1> fromMap, Function<? super V1, V2> function) {
    return Maps.transformValues(fromMap, function);
  }

  public static <K, V>
  Map<K, Set<V>> filterValues(Map<K, Set<V>> fromMap, Predicate<V> predicate) {
    LinkedHashMap<K, Set<V>> result = new LinkedHashMap<>();
    fromMap.forEach((key, value) -> {
      for (V v : value) {
        if (!predicate.test(v)) {
          return;
        }
        result.merge(key, Set.of(v), Util::mutableUnion);
      }
    });
    return result;
  }

  public static <K, V>
  Map<K, V> filterKeys(Map<K, V> fromMap, Predicate<K> predicate) {
    return Maps.filterKeys(fromMap, predicate);
  }

  public static <E> Stream<E> asStream(Iterable<E> coll) {
    if (coll instanceof Collection) {
      return ((Collection<E>) coll).stream();
    }
    return StreamSupport.stream(coll.spliterator(), false);
  }

  public static <E> List<E> reverse(List<E> input) {
    ArrayList<E> result = new ArrayList<>(input);
    Collections.reverse(result);
    return result;
  }
}
