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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.extension.DaggerGraphs.unreachableNodes;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.model.BindingKind.SUBCOMPONENT_CREATOR;
import static io.jbock.auto.common.MoreTypes.asTypeElement;
import static java.util.Objects.requireNonNull;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.binding.BindingGraph.TopLevelBindingGraph;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.extension.DaggerStreams;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.MissingBinding;
import dagger.model.BindingGraph.Node;
import dagger.model.ComponentPath;
import dagger.model.DependencyRequest;
import dagger.spi.model.DaggerExecutableElement;
import dagger.spi.model.DaggerTypeElement;
import dagger.spi.model.Key;
import io.jbock.common.graph.ImmutableNetwork;
import io.jbock.common.graph.MutableNetwork;
import io.jbock.common.graph.Network;
import io.jbock.common.graph.NetworkBuilder;
import jakarta.inject.Inject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** Converts {@link BindingGraph}s to {@link dagger.model.BindingGraph}s. */
final class BindingGraphConverter {
  private final BindingDeclarationFormatter bindingDeclarationFormatter;

  @Inject
  BindingGraphConverter(BindingDeclarationFormatter bindingDeclarationFormatter) {
    this.bindingDeclarationFormatter = bindingDeclarationFormatter;
  }

  /**
   * Creates the external {@link dagger.model.BindingGraph} representing the given internal {@link
   * BindingGraph}.
   */
  BindingGraph convert(LegacyBindingGraph legacyBindingGraph, boolean isFullBindingGraph) {
    MutableNetwork<Node, Edge> network = asNetwork(legacyBindingGraph);
    ComponentNode rootNode = rootComponentNode(network);

    // When bindings are copied down into child graphs because they transitively depend on local
    // multibindings or optional bindings, the parent-owned binding is still there. If that
    // parent-owned binding is not reachable from its component, it doesn't need to be in the graph
    // because it will never be used. So remove all nodes that are not reachable from the root
    // component—unless we're converting a full binding graph.
    if (!isFullBindingGraph) {
      unreachableNodes(network.asGraph(), rootNode).forEach(network::removeNode);
    }

    TopLevelBindingGraph topLevelBindingGraph =
        TopLevelBindingGraph.create(ImmutableNetwork.copyOf(network), isFullBindingGraph);
    return BindingGraph.create(rootNode, topLevelBindingGraph);
  }

  private MutableNetwork<Node, Edge> asNetwork(LegacyBindingGraph graph) {
    Converter converter = new Converter(bindingDeclarationFormatter);
    converter.visitRootComponent(graph);
    return converter.network;
  }

  // TODO(dpb): Example of BindingGraph logic applied to derived networks.
  private ComponentNode rootComponentNode(Network<Node, Edge> network) {
    return network.nodes().stream()
        .flatMap(DaggerStreams.instancesOf(ComponentNode.class))
        .filter(node -> node.componentPath().atRoot())
        .findFirst()
        .orElseThrow();
  }

  /**
   * Used as a cache key to make sure resolved bindings are cached per component path.
   * This is required so that binding nodes are not reused across different branches of the
   * graph since the ResolvedBindings class only contains the component and not the path.
   */
  static final class ResolvedBindingsWithPath {
    private final ResolvedBindings resolvedBindings;
    private final ComponentPath componentPath;

    private ResolvedBindingsWithPath(
        ResolvedBindings resolvedBindings,
        ComponentPath componentPath) {
      this.resolvedBindings = requireNonNull(resolvedBindings);
      this.componentPath = requireNonNull(componentPath);
    }

    ResolvedBindings resolvedBindings() {
      return resolvedBindings;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ResolvedBindingsWithPath that = (ResolvedBindingsWithPath) o;
      return resolvedBindings.equals(that.resolvedBindings)
          && componentPath.equals(that.componentPath);
    }

    @Override
    public int hashCode() {
      return Objects.hash(resolvedBindings, componentPath);
    }

    static ResolvedBindingsWithPath create(
        ResolvedBindings resolvedBindings, ComponentPath componentPath) {
      return new ResolvedBindingsWithPath(resolvedBindings, componentPath);
    }
  }

  private static final class Converter {
    /** The path from the root graph to the currently visited graph. */
    private final Deque<LegacyBindingGraph> bindingGraphPath = new ArrayDeque<>();

    /** The {@link ComponentPath} for each component in {@link #bindingGraphPath}. */
    private final Deque<ComponentPath> componentPaths = new ArrayDeque<>();

    private final BindingDeclarationFormatter bindingDeclarationFormatter;
    private final MutableNetwork<Node, Edge> network =
        NetworkBuilder.directed().allowsParallelEdges(true).allowsSelfLoops(true).build();
    private final Set<BindingNode> bindings = new HashSet<>();

    private final Map<ResolvedBindingsWithPath, Set<BindingNode>> resolvedBindingsMap =
        new HashMap<>();

    /** Constructs a converter for a root (component, not subcomponent) binding graph. */
    private Converter(BindingDeclarationFormatter bindingDeclarationFormatter) {
      this.bindingDeclarationFormatter = bindingDeclarationFormatter;
    }

    private void visitRootComponent(LegacyBindingGraph graph) {
      visitComponent(graph, null);
    }

    /**
     * Called once for each component in a component hierarchy.
     *
     * <p>This implementation does the following:
     *
     * <ol>
     *   <li>If this component is installed in its parent by a subcomponent factory method, calls
     *       {@link #visitSubcomponentFactoryMethod(ComponentNode, ComponentNode,
     *       ExecutableElement)}.
     *   <li>For each entry point in the component, calls {@link #visitEntryPoint(ComponentNode,
     *       DependencyRequest)}.
     *   <li>For each child component, calls {@code visitComponent(LegacyBindingGraph,
     *       ComponentNode)}, updating the traversal state.
     * </ol>
     *
     * @param graph the currently visited graph
     */
    private void visitComponent(LegacyBindingGraph graph, ComponentNode parentComponent) {
      bindingGraphPath.addLast(graph);
      ComponentPath graphPath =
          ComponentPath.create(
              bindingGraphPath.stream()
                  .map(LegacyBindingGraph::componentDescriptor)
                  .map(ComponentDescriptor::typeElement)
                  .map(XTypeElement::toJavac)
                  .map(DaggerTypeElement::fromJava)
                  .collect(toImmutableList()));
      componentPaths.addLast(graphPath);
      ComponentNode currentComponent =
          ComponentNodeImpl.create(componentPath(), graph.componentDescriptor());

      network.addNode(currentComponent);

      for (ComponentMethodDescriptor entryPointMethod :
          graph.componentDescriptor().entryPointMethods()) {
        visitEntryPoint(currentComponent, entryPointMethod.dependencyRequest().orElseThrow());
      }

      for (ResolvedBindings resolvedBindings : graph.resolvedBindings()) {
        for (BindingNode binding : bindingNodes(resolvedBindings)) {
          if (bindings.add(binding)) {
            network.addNode(binding);
            for (DependencyRequest dependencyRequest : binding.dependencies()) {
              addDependencyEdges(binding, dependencyRequest);
            }
          }
          if (binding.kind().equals(SUBCOMPONENT_CREATOR)
              && binding.componentPath().equals(currentComponent.componentPath())) {
            network.addEdge(
                binding,
                subcomponentNode(binding.key().type().java(), graph),
                new SubcomponentCreatorBindingEdgeImpl(
                    resolvedBindings.subcomponentDeclarations()));
          }
        }
      }

      if (bindingGraphPath.size() > 1) {
        Iterator<LegacyBindingGraph> it = bindingGraphPath.descendingIterator();
        it.next();
        LegacyBindingGraph parent = it.next();
        parent
            .componentDescriptor()
            .getFactoryMethodForChildComponent(graph.componentDescriptor())
            .ifPresent(
                childFactoryMethod ->
                    visitSubcomponentFactoryMethod(
                        parentComponent, currentComponent, childFactoryMethod.methodElement()));
      }

      for (LegacyBindingGraph child : graph.subgraphs()) {
        visitComponent(child, currentComponent);
      }

      Preconditions.checkState(bindingGraphPath.removeLast().equals(graph));
      Preconditions.checkState(componentPaths.removeLast().equals(graphPath));
    }

    /**
     * Called once for each entry point in a component.
     *
     * @param componentNode the component that contains the entry point
     * @param entryPoint the entry point to visit
     */
    private void visitEntryPoint(ComponentNode componentNode, DependencyRequest entryPoint) {
      addDependencyEdges(componentNode, entryPoint);
    }

    /**
     * Called if this component was installed in its parent by a subcomponent factory method.
     *
     * @param parentComponent the parent graph
     * @param currentComponent the currently visited graph
     * @param factoryMethod the factory method in the parent component that declares that the
     *     current component is a child
     */
    private void visitSubcomponentFactoryMethod(
        ComponentNode parentComponent,
        ComponentNode currentComponent,
        ExecutableElement factoryMethod) {
      network.addEdge(
          parentComponent,
          currentComponent,
          new ChildFactoryMethodEdgeImpl(DaggerExecutableElement.fromJava(factoryMethod)));
    }

    /**
     * Returns an immutable snapshot of the path from the root component to the currently visited
     * component.
     */
    private ComponentPath componentPath() {
      return componentPaths.getLast();
    }

    /**
     * Returns the subpath from the root component to the matching {@code ancestor} of the current
     * component.
     */
    private ComponentPath pathFromRootToAncestor(TypeElement ancestor) {
      for (ComponentPath componentPath : componentPaths) {
        if (componentPath.currentComponent().java().equals(ancestor)) {
          return componentPath;
        }
      }
      throw new IllegalArgumentException(
          String.format(
              "%s is not in the current path: %s", ancestor.getQualifiedName(), componentPath()));
    }

    /**
     * Returns the LegacyBindingGraph for {@code ancestor}, where {@code ancestor} is in the
     * component path of the current traversal.
     */
    private LegacyBindingGraph graphForAncestor(TypeElement ancestor) {
      for (LegacyBindingGraph graph : bindingGraphPath) {
        if (graph.componentDescriptor().typeElement().toJavac().equals(ancestor)) {
          return graph;
        }
      }
      throw new IllegalArgumentException(
          String.format(
              "%s is not in the current path: %s", ancestor.getQualifiedName(), componentPath()));
    }

    /**
     * Adds a {@link dagger.model.BindingGraph.DependencyEdge} from a node to the binding(s) that
     * satisfy a dependency request.
     */
    private void addDependencyEdges(Node source, DependencyRequest dependencyRequest) {
      ResolvedBindings dependencies = resolvedDependencies(source, dependencyRequest);
      if (dependencies.isEmpty()) {
        addDependencyEdge(source, dependencyRequest, missingBindingNode(dependencies));
      } else {
        for (BindingNode dependency : bindingNodes(dependencies)) {
          addDependencyEdge(source, dependencyRequest, dependency);
        }
      }
    }

    private void addDependencyEdge(
        Node source, DependencyRequest dependencyRequest, Node dependency) {
      network.addNode(dependency);
      if (!hasDependencyEdge(source, dependency, dependencyRequest)) {
        network.addEdge(
            source,
            dependency,
            new DependencyEdgeImpl(dependencyRequest, source instanceof ComponentNode));
      }
    }

    private boolean hasDependencyEdge(
        Node source, Node dependency, DependencyRequest dependencyRequest) {
      // An iterative approach is used instead of a Stream because this method is called in a hot
      // loop, and the Stream calculates the size of network.edgesConnecting(), which is slow. This
      // seems to be because calculating the edges connecting two nodes in a Network that supports
      // parallel edges is must check the equality of many nodes, and BindingNode's equality
      // semantics drag in the equality of many other expensive objects
      for (Edge edge : network.edgesConnecting(source, dependency)) {
        if (edge instanceof DependencyEdge) {
          if (((DependencyEdge) edge).dependencyRequest().equals(dependencyRequest)) {
            return true;
          }
        }
      }
      return false;
    }

    private ResolvedBindings resolvedDependencies(
        Node source, DependencyRequest dependencyRequest) {
      return graphForAncestor(source.componentPath().currentComponent().java())
          .resolvedBindings(bindingRequest(dependencyRequest));
    }

    private Set<BindingNode> bindingNodes(ResolvedBindings resolvedBindings) {
      ResolvedBindingsWithPath resolvedBindingsWithPath =
          ResolvedBindingsWithPath.create(resolvedBindings, componentPath());
      return resolvedBindingsMap.computeIfAbsent(
          resolvedBindingsWithPath, this::uncachedBindingNodes);
    }

    private Set<BindingNode> uncachedBindingNodes(
        ResolvedBindingsWithPath resolvedBindingsWithPath) {
      Set<BindingNode> bindingNodes = new LinkedHashSet<>();
      resolvedBindingsWithPath.resolvedBindings()
          .allBindings()
          .forEach(
              (component, bindings) -> {
                for (Binding binding : bindings) {
                  bindingNodes.add(
                      bindingNode(resolvedBindingsWithPath.resolvedBindings(), binding, component));
                }
              });
      return bindingNodes;
    }

    private BindingNode bindingNode(
        ResolvedBindings resolvedBindings, Binding binding, TypeElement owningComponent) {
      return BindingNode.create(
          pathFromRootToAncestor(owningComponent),
          binding,
          resolvedBindings.subcomponentDeclarations(),
          bindingDeclarationFormatter);
    }

    private MissingBinding missingBindingNode(ResolvedBindings dependencies) {
      // Put all missing binding nodes in the root component. This simplifies the binding graph
      // and produces better error messages for users since all dependents point to the same node.
      return MissingBindingImpl.create(
          ComponentPath.create(List.of(componentPath().rootComponent())),
          dependencies.key());
    }

    private ComponentNode subcomponentNode(
        TypeMirror subcomponentBuilderType, LegacyBindingGraph graph) {
      TypeElement subcomponentBuilderElement = asTypeElement(subcomponentBuilderType);
      ComponentDescriptor subcomponent =
          graph.componentDescriptor().getChildComponentWithBuilderType(subcomponentBuilderElement);
      return ComponentNodeImpl.create(
          componentPath().childPath(DaggerTypeElement.fromJava(subcomponent.typeElement().toJavac())),
          subcomponent);
    }
  }

  static final class MissingBindingImpl extends MissingBinding {
    private final ComponentPath componentPath;
    private final Key key;
    private final IntSupplier hash = Suppliers.memoizeInt(() ->
        Objects.hash(componentPath(), key()));

    MissingBindingImpl(ComponentPath componentPath, Key key) {
      this.componentPath = requireNonNull(componentPath);
      this.key = requireNonNull(key);
    }

    static MissingBinding create(ComponentPath component, Key key) {
      return new MissingBindingImpl(component, key);
    }

    @Override
    public ComponentPath componentPath() {
      return componentPath;
    }

    @Override
    public Key key() {
      return key;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MissingBindingImpl that = (MissingBindingImpl) o;
      return this.hashCode() == that.hashCode()
          && componentPath.equals(that.componentPath)
          && key.equals(that.key);
    }

    @Override
    public int hashCode() {
      return hash.getAsInt();
    }
  }
}
