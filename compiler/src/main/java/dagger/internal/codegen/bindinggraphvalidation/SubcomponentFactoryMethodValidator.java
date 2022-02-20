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

package dagger.internal.codegen.bindinggraphvalidation;

import static dagger.internal.codegen.base.Util.union;
import static dagger.internal.codegen.binding.ComponentRequirement.componentCanMakeNewInstances;
import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static javax.tools.Diagnostic.Kind.ERROR;

import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.binding.ComponentNodeImpl;
import dagger.internal.codegen.xprocessing.XExecutableType;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.spi.model.BindingGraphPlugin;
import dagger.spi.model.DiagnosticReporter;
import dagger.spi.model.Binding;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.spi.model.BindingGraph.ComponentNode;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reports an error if a subcomponent factory method is missing required modules. */
final class SubcomponentFactoryMethodValidator implements BindingGraphPlugin {

  private final Map<ComponentNode, Set<XTypeElement>> inheritedModulesCache = new HashMap<>();

  @Inject
  SubcomponentFactoryMethodValidator() {
  }

  @Override
  public String pluginName() {
    return "Dagger/SubcomponentFactoryMethodMissingModule";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    if (!bindingGraph.rootComponentNode().isRealComponent()
        || bindingGraph.rootComponentNode().isSubcomponent()) {
      // We don't know all the modules that might be owned by the child until we know the real root
      // component, which we don't if the root component node is really a module or a subcomponent.
      return;
    }
    bindingGraph.network().edges().stream()
        .flatMap(instancesOf(ChildFactoryMethodEdge.class))
        .forEach(
            edge -> {
              Set<XTypeElement> missingModules = findMissingModules(edge, bindingGraph);
              if (!missingModules.isEmpty()) {
                reportMissingModuleParameters(
                    edge, missingModules, bindingGraph, diagnosticReporter);
              }
            });
  }

  private Set<XTypeElement> findMissingModules(
      ChildFactoryMethodEdge edge, BindingGraph graph) {
    Set<XTypeElement> factoryMethodParameters =
        subgraphFactoryMethodParameters(edge, graph);
    ComponentNode child = (ComponentNode) graph.network().incidentNodes(edge).target();
    Set<XTypeElement> modulesOwnedByChild = ownedModules(child, graph);
    return graph.bindings().stream()
        // bindings owned by child
        .filter(binding -> binding.componentPath().equals(child.componentPath()))
        // that require a module instance
        .filter(Binding::requiresModuleInstance)
        .map(binding -> binding.contributingModule().get().xprocessing())
        .distinct()
        // module owned by child
        .filter(modulesOwnedByChild::contains)
        // module not in the method parameters
        .filter(module -> !factoryMethodParameters.contains(module))
        // module doesn't have an accessible no-arg constructor
        .filter(moduleType -> !componentCanMakeNewInstances(moduleType))
        .collect(toImmutableSet());
  }

  private Set<XTypeElement> subgraphFactoryMethodParameters(
      ChildFactoryMethodEdge edge, BindingGraph bindingGraph) {
    ComponentNode parent = (ComponentNode) bindingGraph.network().incidentNodes(edge).source();
    bindingGraph.network().incidentNodes(edge).source();
    XType parentType = parent.componentPath().currentComponent().xprocessing().getType();
    XExecutableType factoryMethodType = edge.factoryMethod().xprocessing().asMemberOf(parentType);
    return factoryMethodType.getParameterTypes().stream()
        .map(XType::getTypeElement)
        .collect(toImmutableSet());
  }

  private Set<XTypeElement> ownedModules(ComponentNode component, BindingGraph graph) {
    return Util.difference(
        ((ComponentNodeImpl) component).componentDescriptor().moduleTypes(),
        inheritedModules(component, graph));
  }

  private Set<XTypeElement> inheritedModules(ComponentNode component, BindingGraph graph) {
    return Util.reentrantComputeIfAbsent(
        inheritedModulesCache, component, uncachedInheritedModules(graph));
  }

  private Function<ComponentNode, Set<XTypeElement>> uncachedInheritedModules(BindingGraph graph) {
    return componentNode ->
        componentNode.componentPath().atRoot()
            ? Set.of()
            : graph
            .componentNode(componentNode.componentPath().parent())
            .map(parent -> union(ownedModules(parent, graph), inheritedModules(parent, graph)))
            .orElseThrow();
  }

  private void reportMissingModuleParameters(
      ChildFactoryMethodEdge edge,
      Set<XTypeElement> missingModules,
      BindingGraph graph,
      DiagnosticReporter diagnosticReporter) {
    diagnosticReporter.reportSubcomponentFactoryMethod(
        ERROR,
        edge,
        "%s requires modules which have no visible default constructors. "
            + "Add the following modules as parameters to this method: %s",
        graph
            .network()
            .incidentNodes(edge)
            .target()
            .componentPath()
            .currentComponent()
            .className()
            .canonicalName(),
        missingModules.stream().map(XTypeElement::toString).collect(Collectors.joining(", ")));
  }
}
