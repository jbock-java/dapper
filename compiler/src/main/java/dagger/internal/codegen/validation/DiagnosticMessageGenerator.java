/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.internal.codegen.validation;

import static dagger.internal.codegen.base.ElementFormatter.elementToString;
import static dagger.internal.codegen.extension.DaggerGraphs.shortestPath;
import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.DECLARATION_ORDER;
import static dagger.internal.codegen.langmodel.DaggerElements.closestEnclosingTypeElement;
import static java.util.Collections.min;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

import dagger.internal.codegen.base.ElementFormatter;
import dagger.internal.codegen.base.Formatter;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.binding.DependencyRequestFormatter;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.spi.model.Binding;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraph.DependencyEdge;
import dagger.spi.model.BindingGraph.Edge;
import dagger.spi.model.BindingGraph.MaybeBinding;
import dagger.spi.model.BindingGraph.Node;
import dagger.spi.model.ComponentPath;
import dagger.spi.model.DaggerElement;
import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** Helper class for generating diagnostic messages. */
public final class DiagnosticMessageGenerator {

  /** Injectable factory for {@code DiagnosticMessageGenerator}. */
  public static final class Factory {
    private final DaggerTypes types;
    private final DependencyRequestFormatter dependencyRequestFormatter;
    private final ElementFormatter elementFormatter;

    @Inject
    Factory(
        DaggerTypes types,
        DependencyRequestFormatter dependencyRequestFormatter,
        ElementFormatter elementFormatter) {
      this.types = types;
      this.dependencyRequestFormatter = dependencyRequestFormatter;
      this.elementFormatter = elementFormatter;
    }

    /** Creates a {@code DiagnosticMessageGenerator} for the given binding graph. */
    public DiagnosticMessageGenerator create(BindingGraph graph) {
      return new DiagnosticMessageGenerator(
          graph, types, dependencyRequestFormatter, elementFormatter);
    }
  }

  private final BindingGraph graph;
  private final DependencyRequestFormatter dependencyRequestFormatter;
  private final ElementFormatter elementFormatter;

  /** A cached function from type to all of its supertypes in breadth-first order. */
  private final Function<TypeElement, Iterable<TypeElement>> supertypes;

  /** The shortest path (value) from an entry point (column) to a binding (row). */
  private final Map<MaybeBinding, Map<DependencyEdge, List<Node>>> shortestPaths =
      new LinkedHashMap<>();

  private static <K, V> Function<K, V> memoize(Function<K, V> uncached) {
    HashMap<K, V> map = new HashMap<>();
    return k -> map.computeIfAbsent(k, uncached);
  }

  private DiagnosticMessageGenerator(
      BindingGraph graph,
      DaggerTypes types,
      DependencyRequestFormatter dependencyRequestFormatter,
      ElementFormatter elementFormatter) {
    this.graph = graph;
    this.dependencyRequestFormatter = dependencyRequestFormatter;
    this.elementFormatter = elementFormatter;
    supertypes =
        memoize(component ->
            StreamSupport.stream(types.supertypes(component.asType()).spliterator(), false)
                .map(MoreTypes::asTypeElement).collect(Collectors.toList()));
  }

  public String getMessage(MaybeBinding binding) {
    Set<DependencyEdge> entryPoints = graph.entryPointEdgesDependingOnBinding(binding);
    Set<DependencyEdge> requests = requests(binding);
    List<DependencyEdge> dependencyTrace = dependencyTrace(binding, entryPoints);

    return getMessageInternal(dependencyTrace, requests, entryPoints);
  }

  public String getMessage(DependencyEdge dependencyEdge) {
    Set<DependencyEdge> requests = Set.of(dependencyEdge);

    Set<DependencyEdge> entryPoints;
    List<DependencyEdge> dependencyTrace;
    if (dependencyEdge.isEntryPoint()) {
      entryPoints = Set.of(dependencyEdge);
      dependencyTrace = List.of(dependencyEdge);
    } else {
      // It's not an entry point, so it's part of a binding
      Binding binding = (Binding) source(dependencyEdge);
      entryPoints = graph.entryPointEdgesDependingOnBinding(binding);
      dependencyTrace = Stream.concat(Stream.of(dependencyEdge), dependencyTrace(binding, entryPoints).stream())
          .collect(Collectors.toList());
    }

    return getMessageInternal(dependencyTrace, requests, entryPoints);
  }

  private String getMessageInternal(
      List<DependencyEdge> dependencyTrace,
      Set<DependencyEdge> requests,
      Set<DependencyEdge> entryPoints) {
    StringBuilder message =
        graph.isFullBindingGraph()
            ? new StringBuilder()
            : new StringBuilder(dependencyTrace.size() * 100 /* a guess heuristic */);

    // Print the dependency trace unless it's a full binding graph
    if (!graph.isFullBindingGraph()) {
      dependencyTrace.forEach(
          edge -> dependencyRequestFormatter.appendFormatLine(message, edge.dependencyRequest()));
      if (!dependencyTrace.isEmpty()) {
        DependencyEdge last = dependencyTrace.get(dependencyTrace.size() - 1);
        appendComponentPathUnlessAtRoot(message, source(last));
      }
    }
    message.append(getRequestsNotInTrace(dependencyTrace, requests, entryPoints));
    return message.toString();
  }


  public String getRequestsNotInTrace(
      List<DependencyEdge> dependencyTrace,
      Set<DependencyEdge> requests,
      Set<DependencyEdge> entryPoints) {
    StringBuilder message = new StringBuilder();

    // Print any dependency requests that aren't shown as part of the dependency trace.
    Set<Element> requestsToPrint =
        requests.stream()
            // if printing entry points, skip entry points and the traced request
            .filter(
                request ->
                    graph.isFullBindingGraph()
                        || (!request.isEntryPoint() && !isTracedRequest(dependencyTrace, request)))
            .map(request -> request.dependencyRequest().requestElement())
            .flatMap(Optional::stream)
            .map(DaggerElement::java)
            .collect(toImmutableSet());
    if (!requestsToPrint.isEmpty()) {
      message
          .append("\nIt is")
          .append(graph.isFullBindingGraph() ? " " : " also ")
          .append("requested at:");
      elementFormatter.formatIndentedList(message, requestsToPrint, 1);
    }

    // Print the remaining entry points, showing which component they're in, unless it's a full
    // binding graph
    if (!graph.isFullBindingGraph() && entryPoints.size() > 1) {
      message.append("\nThe following other entry points also depend on it:");
      entryPointFormatter.formatIndentedList(
          message,
          entryPoints.stream()
              .filter(entryPoint -> !entryPoint.equals(dependencyTrace.get(dependencyTrace.size() - 1)))
              .sorted(
                  // 1. List entry points in components closest to the root first.
                  // 2. List entry points declared in a component before those in a supertype.
                  // 3. List entry points in declaration order in their declaring type.
                  rootComponentFirst()
                      .thenComparing(nearestComponentSupertypeFirst())
                      .thenComparing(requestElementDeclarationOrder()))
              .collect(toImmutableList()),
          1);
    }
    return message.toString();
  }

  public void appendComponentPathUnlessAtRoot(StringBuilder message, Node node) {
    if (!node.componentPath().equals(graph.rootComponentNode().componentPath())) {
      message.append(String.format(" [%s]", node.componentPath()));
    }
  }

  private final Formatter<DependencyEdge> entryPointFormatter =
      new Formatter<>() {
        @Override
        public String format(DependencyEdge object) {
          Element requestElement = object.dependencyRequest().requestElement().orElseThrow().java();
          StringBuilder element = new StringBuilder(elementToString(requestElement));

          // For entry points declared in subcomponents or supertypes of the root component,
          // append the component path to make clear to the user which component it's in.
          ComponentPath componentPath = source(object).componentPath();
          if (!componentPath.atRoot()
              || !requestElement.getEnclosingElement().equals(componentPath.rootComponent().java())) {
            element.append(String.format(" [%s]", componentPath));
          }
          return element.toString();
        }
      };

  private static boolean isTracedRequest(
      List<DependencyEdge> dependencyTrace, DependencyEdge request) {
    return !dependencyTrace.isEmpty() && request.equals(dependencyTrace.get(0));
  }

  /**
   * Returns the dependency trace from one of the {@code entryPoints} to {@code binding} to {@code
   * message} as a list <i>ending with</i> the entry point.
   */
  // TODO(ronshapiro): Adding a DependencyPath type to dagger.model could be useful, i.e.
  // bindingGraph.shortestPathFromEntryPoint(DependencyEdge, MaybeBindingNode)
  public List<DependencyEdge> dependencyTrace(
      MaybeBinding binding, Set<DependencyEdge> entryPoints) {
    // Module binding graphs may have bindings unreachable from any entry points. If there are
    // no entry points for this DiagnosticInfo, don't try to print a dependency trace.
    if (entryPoints.isEmpty()) {
      return List.of();
    }
    // Show the full dependency trace for one entry point.
    DependencyEdge entryPointForTrace =
        min(
            entryPoints,
            // prefer entry points in components closest to the root
            rootComponentFirst()
                // then prefer entry points with a short dependency path to the error
                .thenComparing(shortestDependencyPathFirst(binding))
                // then prefer entry points declared in the component to those declared in a
                // supertype
                .thenComparing(nearestComponentSupertypeFirst())
                // finally prefer entry points declared first in their enclosing type
                .thenComparing(requestElementDeclarationOrder()));

    List<Node> shortestBindingPath =
        shortestPathFromEntryPoint(entryPointForTrace, binding);
    Preconditions.checkState(
        !shortestBindingPath.isEmpty(),
        "no dependency path from %s to %s in %s",
        entryPointForTrace,
        binding,
        graph);

    List<DependencyEdge> dependencyTrace = new ArrayList<>();
    dependencyTrace.add(entryPointForTrace);
    for (int i = 0; i < shortestBindingPath.size() - 1; i++) {
      Set<Edge> dependenciesBetween =
          graph
              .network()
              .edgesConnecting(shortestBindingPath.get(i), shortestBindingPath.get(i + 1));
      // If a binding requests a key more than once, any of them should be fine to get to the
      // shortest path
      dependencyTrace.add((DependencyEdge) dependenciesBetween.iterator().next());
    }
    Collections.reverse(dependencyTrace);
    return dependencyTrace;
  }

  /** Returns all the nonsynthetic dependency requests for a binding. */
  public Set<DependencyEdge> requests(MaybeBinding binding) {
    return graph.network().inEdges(binding).stream()
        .flatMap(instancesOf(DependencyEdge.class))
        .filter(edge -> edge.dependencyRequest().requestElement().isPresent())
        .sorted(requestEnclosingTypeName().thenComparing(requestElementDeclarationOrder()))
        .collect(toImmutableSet());
  }

  /**
   * Returns a comparator that sorts entry points in components whose paths from the root are
   * shorter first.
   */
  Comparator<DependencyEdge> rootComponentFirst() {
    return comparingInt(entryPoint -> source(entryPoint).componentPath().components().size());
  }

  /**
   * Returns a comparator that puts entry points whose shortest dependency path to {@code binding}
   * is shortest first.
   */
  Comparator<DependencyEdge> shortestDependencyPathFirst(MaybeBinding binding) {
    return comparing(entryPoint -> shortestPathFromEntryPoint(entryPoint, binding).size());
  }

  List<Node> shortestPathFromEntryPoint(DependencyEdge entryPoint, MaybeBinding binding) {
    return shortestPaths
        .computeIfAbsent(binding, k -> new LinkedHashMap<>())
        .computeIfAbsent(
            entryPoint,
            ep ->
                shortestPath(
                    node ->
                        graph.network().successors(node).stream().filter(MaybeBinding.class::isInstance)
                            .collect(Collectors.toList()),
                    graph.network().incidentNodes(ep).target(),
                    binding));
  }

  /**
   * Returns a comparator that sorts entry points in by the distance of the type that declares them
   * from the type of the component that contains them.
   *
   * <p>For instance, an entry point declared directly in the component type would sort before one
   * declared in a direct supertype, which would sort before one declared in a supertype of a
   * supertype.
   */
  Comparator<DependencyEdge> nearestComponentSupertypeFirst() {
    return comparingInt(
        entryPoint -> {
          List<TypeElement> types = Util.listOf(supertypes.apply(componentContainingEntryPoint(entryPoint)));
          TypeElement target = typeDeclaringEntryPoint(entryPoint);
          return IntStream.range(0, types.size())
              .filter(i -> types.get(i).equals(target))
              .findFirst().orElse(Integer.MAX_VALUE);
        });
  }

  TypeElement componentContainingEntryPoint(DependencyEdge entryPoint) {
    return source(entryPoint).componentPath().currentComponent().java();
  }

  TypeElement typeDeclaringEntryPoint(DependencyEdge entryPoint) {
    return MoreElements.asType(
        entryPoint.dependencyRequest().requestElement().orElseThrow().java().getEnclosingElement());
  }

  /**
   * Returns a comparator that sorts dependency edges lexicographically by the qualified name of the
   * type that contains them. Only appropriate for edges with request elements.
   */
  Comparator<DependencyEdge> requestEnclosingTypeName() {
    return comparing(
        edge ->
            closestEnclosingTypeElement(edge.dependencyRequest().requestElement().orElseThrow().java())
                .getQualifiedName()
                .toString());
  }

  /**
   * Returns a comparator that sorts edges in the order in which their request elements were
   * declared in their declaring type.
   *
   * <p>Only useful to compare edges whose request elements were declared in the same type.
   */
  Comparator<DependencyEdge> requestElementDeclarationOrder() {
    return comparing(edge -> edge.dependencyRequest().requestElement().orElseThrow().java(), DECLARATION_ORDER);
  }

  private Node source(Edge edge) {
    return graph.network().incidentNodes(edge).source();
  }
}
