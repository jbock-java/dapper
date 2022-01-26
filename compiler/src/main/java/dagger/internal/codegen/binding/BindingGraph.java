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

import static dagger.internal.codegen.base.Suppliers.memoize;
import static dagger.internal.codegen.base.Util.union;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;
import static dagger.internal.codegen.extension.DaggerStreams.stream;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.model.BindingGraph.Edge;
import static dagger.model.BindingGraph.MissingBinding;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.Network;
import com.google.common.graph.Traverser;
import dagger.Subcomponent;
import dagger.internal.codegen.base.TarjanSCCs;
import dagger.internal.codegen.base.Util;
import dagger.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.Node;
import dagger.model.ComponentPath;
import dagger.model.DependencyRequest;
import dagger.spi.model.Key;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * A graph that represents a single component or subcomponent within a fully validated top-level
 * binding graph.
 */
public final class BindingGraph {

  private final Supplier<List<BindingGraph>> subgraphs = memoize(() ->
      topLevelBindingGraph().subcomponentNodes(componentNode()).stream()
          .map(subcomponent -> create(Optional.of(this), subcomponent, topLevelBindingGraph()))
          .collect(toImmutableList()));

  private final Supplier<Map<ComponentPath, ComponentDescriptor>> componentDescriptorsByPath = memoize(() ->
      topLevelBindingGraph().componentNodes().stream()
          .map(ComponentNodeImpl.class::cast)
          .collect(
              toImmutableMap(ComponentNode::componentPath, ComponentNodeImpl::componentDescriptor)));

  private final ComponentNode componentNode;
  private final dagger.internal.codegen.binding.BindingGraph.TopLevelBindingGraph topLevelBindingGraph;

  private BindingGraph(
      ComponentNode componentNode,
      dagger.internal.codegen.binding.BindingGraph.TopLevelBindingGraph topLevelBindingGraph) {
    this.componentNode = requireNonNull(componentNode);
    this.topLevelBindingGraph = requireNonNull(topLevelBindingGraph);
  }

  private final Supplier<Set<ComponentRequirement>> componentRequirements = memoize(() -> {
    Set<TypeElement> requiredModules =
        stream(Traverser.forTree(BindingGraph::subgraphs).depthFirstPostOrder(this))
            .flatMap(graph -> graph.bindingModules.stream())
            .filter(ownedModuleTypes()::contains)
            .collect(toImmutableSet());
    Set<ComponentRequirement> requirements = new LinkedHashSet<>();
    componentDescriptor().requirements().stream()
        .filter(
            requirement ->
                !requirement.kind().isModule()
                    || requiredModules.contains(requirement.typeElement()))
        .forEach(requirements::add);
    if (factoryMethod().isPresent()) {
      requirements.addAll(factoryMethodParameters().keySet());
    }
    return requirements;
  });

  /**
   * A graph that represents the entire network of nodes from all components, subcomponents and
   * their bindings.
   */
  public static final class TopLevelBindingGraph extends dagger.model.BindingGraph {

    private final Supplier<Map<ComponentPath, List<BindingNode>>> bindingsByComponent = memoize(() -> bindings().stream().map(BindingNode.class::cast).collect(
        Collectors.groupingBy(Node::componentPath)));

    private final Supplier<Comparator<Node>> nodeOrder = memoize(() -> {
      Map<Node, Integer> nodeOrderMap = new HashMap<>(Math.max(10, (int) (1.5 * network().nodes().size())));
      int i = 0;
      for (Node node : network().nodes()) {
        nodeOrderMap.put(node, i++);
      }
      return Comparator.comparing(nodeOrderMap::get);
    });

    private final Supplier<Set<Set<Node>>> stronglyConnectedNodes = memoize(() -> TarjanSCCs.compute(
        network().nodes(),
        // NetworkBuilder does not have a stable successor order, so we have to roll our own
        // based on the node order, which is stable.
        // TODO(bcorso): Fix once https://github.com/google/guava/issues/2650 is fixed.
        node ->
            network().successors(node).stream().sorted(nodeOrder()).collect(toImmutableList())));

    private final boolean isFullBindingGraph;
    private final Map<ComponentPath, ComponentNode> mComponentNodes;
    private final Map<ComponentNode, Set<ComponentNode>> mSubcomponentNodes;
    private final Set<Binding> frameworkTypeBindings;

    TopLevelBindingGraph(
        ImmutableNetwork<Node, Edge> network,
        Set<dagger.model.Binding> bindings,
        Set<MissingBinding> missingBindings,
        Set<ComponentNode> componentNodes,
        boolean isFullBindingGraph,
        Map<ComponentPath, ComponentNode> mComponentNodes,
        Map<ComponentNode, Set<ComponentNode>> mSubcomponentNodes,
        Set<Binding> frameworkTypeBindings) {
      super(network, bindings, missingBindings, componentNodes);
      this.isFullBindingGraph = isFullBindingGraph;
      this.mComponentNodes = mComponentNodes;
      this.mSubcomponentNodes = mSubcomponentNodes;
      this.frameworkTypeBindings = frameworkTypeBindings;
    }

    @Override
    public boolean isFullBindingGraph() {
      return isFullBindingGraph;
    }

    static TopLevelBindingGraph create(
        ImmutableNetwork<Node, Edge> network, boolean isFullBindingGraph) {
      NodesByClass nodesByClass = NodesByClass.create(network);

      Map<ComponentPath, ComponentNode> mComponentNodes =
          nodesByClass.componentNodes.stream()
              .collect(
                  toImmutableMap(ComponentNode::componentPath, componentNode -> componentNode));

      Map<ComponentNode, Set<ComponentNode>> subcomponentNodesBuilder =
          new LinkedHashMap<>();
      nodesByClass.componentNodes.stream()
          .filter(componentNode -> !componentNode.componentPath().atRoot())
          .forEach(
              componentNode -> {
                ComponentNode key = mComponentNodes.get(componentNode.componentPath().parent());
                subcomponentNodesBuilder.merge(key, Set.of(componentNode), Util::mutableUnion);
              });
      Set<Binding> frameworkTypeBindings = frameworkRequestBindingSet(network, nodesByClass.bindings);
      return new TopLevelBindingGraph(network,
          nodesByClass.bindings, nodesByClass.missingBindings, nodesByClass.componentNodes,
          isFullBindingGraph, mComponentNodes, subcomponentNodesBuilder,
          frameworkTypeBindings);
    }

    // This overrides dagger.model.BindingGraph with a more efficient implementation.
    @Override
    public Optional<ComponentNode> componentNode(ComponentPath componentPath) {
      return mComponentNodes.containsKey(componentPath)
          ? Optional.of(mComponentNodes.get(componentPath))
          : Optional.empty();
    }

    /** Returns the set of subcomponent nodes of the given component node. */
    Set<ComponentNode> subcomponentNodes(ComponentNode componentNode) {
      return mSubcomponentNodes.getOrDefault(componentNode, Set.of());
    }

    /**
     * Returns an index of each {@link BindingNode} by its {@link ComponentPath}. Accessing this for
     * a component and its parent components is faster than doing a graph traversal.
     */
    Map<ComponentPath, List<BindingNode>> bindingsByComponent() {
      return bindingsByComponent.get();
    }

    /** Returns a {@link Comparator} in the same order as {@link Network#nodes()}. */
    Comparator<Node> nodeOrder() {
      return nodeOrder.get();
    }

    /** Returns the set of strongly connected nodes in this graph in reverse topological order. */
    public Set<Set<Node>> stronglyConnectedNodes() {
      return stronglyConnectedNodes.get();
    }

    public boolean hasFrameworkRequest(Binding binding) {
      return frameworkTypeBindings.contains(binding);
    }

    private static Set<Binding> frameworkRequestBindingSet(
        ImmutableNetwork<Node, Edge> network, Set<dagger.model.Binding> bindings) {
      Set<Binding> frameworkRequestBindings = new HashSet<>();
      for (dagger.model.Binding binding : bindings) {
        List<DependencyEdge> edges =
            network.inEdges(binding).stream()
                .flatMap(instancesOf(DependencyEdge.class))
                .collect(Collectors.toList());
        for (DependencyEdge edge : edges) {
          DependencyRequest request = edge.dependencyRequest();
          switch (request.kind()) {
            case INSTANCE:
              continue;
            case PROVIDER_OF_LAZY:
            case LAZY:
            case PROVIDER:
              frameworkRequestBindings.add(((BindingNode) binding).delegate());
              break;
          }
        }
      }
      return frameworkRequestBindings;
    }
  }

  private static final class NodesByClass {
    final Set<dagger.model.Binding> bindings;
    final Set<MissingBinding> missingBindings;
    final Set<ComponentNode> componentNodes;

    NodesByClass(
        Set<dagger.model.Binding> bindings,
        Set<MissingBinding> missingBindings,
        Set<ComponentNode> componentNodes) {
      this.bindings = bindings;
      this.missingBindings = missingBindings;
      this.componentNodes = componentNodes;
    }

    static NodesByClass create(ImmutableNetwork<Node, Edge> network) {
      Set<dagger.model.Binding> bindings = network.nodes().stream()
          .filter(node -> node instanceof dagger.model.Binding)
          .map(dagger.model.Binding.class::cast)
          .collect(toCollection(LinkedHashSet::new));
      Set<MissingBinding> missingBindings = network.nodes().stream()
          .filter(node -> node instanceof MissingBinding)
          .map(MissingBinding.class::cast)
          .collect(toCollection(LinkedHashSet::new));
      Set<ComponentNode> componentNodes = network.nodes().stream()
          .filter(node -> node instanceof ComponentNode)
          .map(ComponentNode.class::cast)
          .collect(toCollection(LinkedHashSet::new));
      return new NodesByClass(bindings, missingBindings, componentNodes);
    }
  }

  static BindingGraph create(
      ComponentNode componentNode, TopLevelBindingGraph topLevelBindingGraph) {
    return create(Optional.empty(), componentNode, topLevelBindingGraph);
  }

  private static BindingGraph create(
      Optional<BindingGraph> parent,
      ComponentNode componentNode,
      TopLevelBindingGraph topLevelBindingGraph) {
    // TODO(bcorso): Mapping binding nodes by key is flawed since bindings that depend on local
    // multibindings can have multiple nodes (one in each component). In this case, we choose the
    // node in the child-most component since this is likely the node that users of this
    // BindingGraph will want (and to remain consistent with LegacyBindingGraph). However, ideally
    // we would avoid this ambiguity by getting dependencies directly from the top-level network.
    // In particular, rather than using a Binding's list of DependencyRequests (which only
    // contains the key) we would use the top-level network to find the DependencyEdges for a
    // particular BindingNode.
    Map<Key, BindingNode> contributionBindings = new LinkedHashMap<>();

    // Construct the maps of the ContributionBindings and MembersInjectionBindings by iterating
    // bindings from this component and then from each successive parent. If a binding exists in
    // multple components, this order ensures that the child-most binding is always chosen first.
    Stream.iterate(componentNode.componentPath(), ComponentPath::parent)
        // Stream.iterate is inifinte stream so we need limit it to the known size of the path.
        .limit(componentNode.componentPath().components().size())
        .flatMap(path -> topLevelBindingGraph.bindingsByComponent().getOrDefault(path, List.of()).stream())
        .forEach(
            bindingNode -> contributionBindings.putIfAbsent(bindingNode.key(), bindingNode));

    BindingGraph bindingGraph = new BindingGraph(componentNode, topLevelBindingGraph);

    Set<ModuleDescriptor> modules =
        ((ComponentNodeImpl) componentNode).componentDescriptor().modules();

    Set<ModuleDescriptor> inheritedModules = parent.map(graph ->
            union(graph.ownedModules, graph.inheritedModules))
        .orElse(Set.of());

    // Set these fields directly on the instance rather than passing these in as input to the
    // AutoValue to prevent exposing this data outside of the class.
    bindingGraph.inheritedModules = inheritedModules;
    bindingGraph.ownedModules = Util.difference(modules, inheritedModules);
    bindingGraph.contributionBindings = Map.copyOf(contributionBindings);
    bindingGraph.bindingModules =
        contributionBindings.values().stream()
            .map(BindingNode::contributingModule)
            .flatMap(Optional::stream)
            .collect(toImmutableSet());

    return bindingGraph;
  }

  private Map<Key, BindingNode> contributionBindings;
  private Set<ModuleDescriptor> inheritedModules;
  private Set<ModuleDescriptor> ownedModules;
  private Set<TypeElement> bindingModules;

  /** Returns the {@link ComponentNode} for this graph. */
  public ComponentNode componentNode() {
    return componentNode;
  }

  /** Returns the {@link ComponentPath} for this graph. */
  public ComponentPath componentPath() {
    return componentNode().componentPath();
  }

  /** Returns the {@link TopLevelBindingGraph} from which this graph is contained. */
  public dagger.internal.codegen.binding.BindingGraph.TopLevelBindingGraph topLevelBindingGraph() {
    return topLevelBindingGraph;
  }

  /** Returns the {@link ComponentDescriptor} for this graph */
  public ComponentDescriptor componentDescriptor() {
    return ((ComponentNodeImpl) componentNode()).componentDescriptor();
  }

  /**
   * Returns the {@link ContributionBinding} for the given {@link Key} in this component or {@link
   * Optional#empty()} if one doesn't exist.
   */
  public Optional<Binding> localContributionBinding(Key key) {
    return contributionBindings.containsKey(key)
        ? Optional.of(contributionBindings.get(key))
        .filter(bindingNode -> bindingNode.componentPath().equals(componentPath()))
        .map(BindingNode::delegate)
        : Optional.empty();
  }

  /** Returns the {@link ContributionBinding} for the given {@link Key}. */
  public ContributionBinding contributionBinding(Key key) {
    return (ContributionBinding) contributionBindings.get(key).delegate();
  }

  /** Returns the {@link TypeElement} for the component this graph represents. */
  public TypeElement componentTypeElement() {
    return componentPath().currentComponent();
  }

  /**
   * Returns the set of modules that are owned by this graph regardless of whether or not any of
   * their bindings are used in this graph. For graphs representing top-level {@link
   * dagger.Component components}, this set will be the same as {@linkplain
   * ComponentDescriptor#modules() the component's transitive modules}. For {@linkplain Subcomponent
   * subcomponents}, this set will be the transitive modules that are not owned by any of their
   * ancestors.
   */
  public Set<TypeElement> ownedModuleTypes() {
    return ownedModules.stream().map(ModuleDescriptor::moduleElement).collect(toImmutableSet());
  }

  /**
   * Returns the factory method for this subcomponent, if it exists.
   *
   * <p>This factory method is the one defined in the parent component's interface.
   *
   * <p>In the example below, the {@code factoryMethod} for {@code ChildComponent}
   * would return the {@link ExecutableElement}: {@code childComponent(ChildModule1)} .
   *
   * <pre><code>
   *   {@literal @Component}
   *   interface ParentComponent {
   *     ChildComponent childComponent(ChildModule1 childModule);
   *   }
   * </code></pre>
   */
  // TODO(b/73294201): Consider returning the resolved ExecutableType for the factory method.
  public Optional<ExecutableElement> factoryMethod() {
    return topLevelBindingGraph().network().inEdges(componentNode()).stream()
        .filter(edge -> edge instanceof ChildFactoryMethodEdge)
        .map(edge -> ((ChildFactoryMethodEdge) edge).factoryMethod())
        .collect(toOptional());
  }

  /**
   * Returns a map between the {@linkplain ComponentRequirement component requirement} and the
   * corresponding {@link VariableElement} for each module parameter in the {@linkplain
   * BindingGraph#factoryMethod factory method}.
   */
  // TODO(dpb): Consider disallowing modules if none of their bindings are used.
  public Map<ComponentRequirement, VariableElement> factoryMethodParameters() {
    return factoryMethod().orElseThrow().getParameters().stream()
        .collect(
            toImmutableMap(
                parameter -> ComponentRequirement.forModule(parameter.asType()),
                parameter -> parameter));
  }

  /**
   * The types for which the component needs instances.
   *
   * <ul>
   *   <li>component dependencies
   *   <li>owned modules with concrete instance bindings that are used in the graph
   *   <li>bound instances
   * </ul>
   */
  public Set<ComponentRequirement> componentRequirements() {
    return componentRequirements.get();
  }

  /**
   * Returns all {@link ComponentDescriptor}s in the {@link TopLevelBindingGraph} mapped by the
   * component path.
   */
  public Map<ComponentPath, ComponentDescriptor> componentDescriptorsByPath() {
    return componentDescriptorsByPath.get();
  }

  public List<BindingGraph> subgraphs() {
    return subgraphs.get();
  }

  /** Returns the list of all {@link BindingNode}s local to this component. */
  public List<BindingNode> localBindingNodes() {
    return topLevelBindingGraph().bindingsByComponent().getOrDefault(componentPath(), List.of());
  }
}
