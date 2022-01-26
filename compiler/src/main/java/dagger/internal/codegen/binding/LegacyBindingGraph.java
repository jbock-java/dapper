/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.internal.codegen.binding;

import static java.util.stream.Collectors.groupingBy;

import dagger.spi.model.Key;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;

// TODO(bcorso): Remove the LegacyBindingGraph after we've migrated to the new BindingGraph.

/** The canonical representation of a full-resolved graph. */
final class LegacyBindingGraph {
  private final ComponentDescriptor componentDescriptor;
  private final Map<Key, ResolvedBindings> contributionBindings;
  private final List<LegacyBindingGraph> subgraphs;

  LegacyBindingGraph(
      ComponentDescriptor componentDescriptor,
      Map<Key, ResolvedBindings> contributionBindings,
      List<LegacyBindingGraph> subgraphs) {
    this.componentDescriptor = componentDescriptor;
    this.contributionBindings = contributionBindings;
    this.subgraphs = checkForDuplicates(subgraphs);
  }

  ComponentDescriptor componentDescriptor() {
    return componentDescriptor;
  }

  ResolvedBindings resolvedBindings(BindingRequest request) {
    return contributionBindings.get(request.key());
  }

  Collection<ResolvedBindings> resolvedBindings() {
    // Don't return an immutable collection - this is only ever used for looping over all bindings
    // in the graph. Copying is wasteful, especially if is a hashing collection, since the values
    // should all, by definition, be distinct.
    return contributionBindings.values();
  }

  List<LegacyBindingGraph> subgraphs() {
    return subgraphs;
  }

  private static List<LegacyBindingGraph> checkForDuplicates(
      List<LegacyBindingGraph> graphs) {
    Map<TypeElement, Collection<LegacyBindingGraph>> duplicateGraphs =
        graphs.stream()
            .collect(groupingBy(graph -> graph.componentDescriptor().typeElement(), LinkedHashMap::new, Collectors.toList()))
            .entrySet().stream()
            .filter(overlapping -> overlapping.getValue().size() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    if (!duplicateGraphs.isEmpty()) {
      throw new IllegalArgumentException("Expected no duplicates: " + duplicateGraphs);
    }
    return graphs;
  }
}
