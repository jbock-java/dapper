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

package dagger.internal.codegen.extension;

import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.collect.ImmutableList;
import dagger.internal.codegen.collect.ImmutableMap;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.collect.SetMultimap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Utilities for streams. */
public final class DaggerStreams {

  /**
   * Returns a {@link Collector} that accumulates the input elements into a new {@link
   * List}, in encounter order.
   */
  // TODO(b/68008628): Use ImmutableList.toImmutableList().
  public static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
    return Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf);
  }

  /**
   * Returns a {@link Collector} that accumulates the input elements into a new {@link
   * Set}, in encounter order.
   */
  // TODO(b/68008628): Use ImmutableSet.toImmutableSet().
  public static <T> Collector<T, ?, ImmutableSet<T>> toImmutableSet() {
    return Collectors.collectingAndThen(toSet(), ImmutableSet::copyOf);
  }

  public static <T> Collector<T, ?, Set<T>> toSet() {
    return Collectors.toCollection(LinkedHashSet::new);
  }

  /**
   * Returns a {@link Collector} that accumulates elements into an {@code Map} whose keys
   * and values are the result of applying the provided mapping functions to the input elements.
   * Entries appear in the result {@code Map} in encounter order.
   */
  public static <T, K, V> Collector<T, ?, ImmutableMap<K, V>> toImmutableMap(
      Function<? super T, K> keyMapper, Function<? super T, V> valueMapper) {
    return Collectors.collectingAndThen(Collectors.toMap(keyMapper, valueMapper, (v1, v2) -> {
      throw new IllegalStateException("found 2 values for the same key: " + v1 + ", " + v2);
    }, LinkedHashMap::new), ImmutableMap::copyOf);
  }

  /**
   * Returns a {@link Collector} that accumulates elements into a {@code Map}
   * whose keys and values are the result of applying the provided mapping functions to the input
   * elements. Entries appear in the result {@code Map} in encounter order.
   */
  public static <T, K, V> Collector<T, ?, SetMultimap<K, V>> toImmutableSetMultimap(
      Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends V> valueMapper) {
    return Collectors.collectingAndThen(
        Collectors.mapping(
        value -> {
          Set<V> values = Set.of(valueMapper.apply(value));
          return new SimpleImmutableEntry<>(keyMapper.apply(value), values);
        },
        Collector.<Map.Entry<K, Set<V>>, LinkedHashMap<K, Set<V>>>of(
            LinkedHashMap::new,
            (map, entry) ->
                map.merge(entry.getKey(), entry.getValue(), Util::mutableUnion),
            (left, right) -> {
              right.forEach((k, v) ->
                  left.merge(k, v, Util::mutableUnion));
              return left;
            })), SetMultimap::new);
  }


  /**
   * Returns a function from {@link Object} to {@code Stream<T>}, which returns a stream containing
   * its input if its input is an instance of {@code T}.
   *
   * <p>Use as an argument to {@link Stream#flatMap(Function)}:
   *
   * <pre>{@code Stream<Bar>} barStream = fooStream.flatMap(instancesOf(Bar.class));</pre>
   */
  public static <T> Function<Object, Stream<T>> instancesOf(Class<T> to) {
    return f -> to.isInstance(f) ? Stream.of(to.cast(f)) : Stream.empty();
  }

  /**
   * Returns a sequential {@link Stream} of the contents of {@code iterable}, delegating to {@link
   * Collection#stream} if possible.
   */
  public static <T> Stream<T> stream(Iterable<T> iterable) {
    return (iterable instanceof Collection)
        ? ((Collection<T>) iterable).stream()
        : StreamSupport.stream(iterable.spliterator(), false);
  }

  private DaggerStreams() {
  }
}
