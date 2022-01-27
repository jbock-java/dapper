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
import static io.jbock.auto.common.MoreTypes.asDeclared;
import static io.jbock.auto.common.MoreTypes.asExecutable;
import static io.jbock.auto.common.MoreTypes.asTypeElements;
import static javax.tools.Diagnostic.Kind.ERROR;

import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.binding.ComponentNodeImpl;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Binding;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.model.BindingGraph.ComponentNode;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;

/** Reports an error if a subcomponent factory method is missing required modules. */
final class SubcomponentFactoryMethodValidator implements BindingGraphPlugin {

  private final DaggerTypes types;
  private final Map<ComponentNode, Set<TypeElement>> inheritedModulesCache = new HashMap<>();

  @Inject
  SubcomponentFactoryMethodValidator(DaggerTypes types) {
    this.types = types;
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
              Set<TypeElement> missingModules = findMissingModules(edge, bindingGraph);
              if (!missingModules.isEmpty()) {
                reportMissingModuleParameters(
                    edge, missingModules, bindingGraph, diagnosticReporter);
              }
            });
  }

  private Set<TypeElement> findMissingModules(
      ChildFactoryMethodEdge edge, BindingGraph graph) {
    Set<TypeElement> factoryMethodParameters =
        subgraphFactoryMethodParameters(edge, graph);
    ComponentNode child = (ComponentNode) graph.network().incidentNodes(edge).target();
    Set<TypeElement> modulesOwnedByChild = ownedModules(child, graph);
    return graph.bindings().stream()
        // bindings owned by child
        .filter(binding -> binding.componentPath().equals(child.componentPath()))
        // that require a module instance
        .filter(Binding::requiresModuleInstance)
        .map(binding -> binding.contributingModule().orElseThrow())
        .distinct()
        // module owned by child
        .filter(modulesOwnedByChild::contains)
        // module not in the method parameters
        .filter(module -> !factoryMethodParameters.contains(module))
        // module doesn't have an accessible no-arg constructor
        .filter(moduleType -> !componentCanMakeNewInstances(moduleType))
        .collect(toImmutableSet());
  }

  private Set<TypeElement> subgraphFactoryMethodParameters(
      ChildFactoryMethodEdge edge, BindingGraph bindingGraph) {
    ComponentNode parent = (ComponentNode) bindingGraph.network().incidentNodes(edge).source();
    DeclaredType parentType = asDeclared(parent.componentPath().currentComponent().asType());
    ExecutableType factoryMethodType =
        asExecutable(types.asMemberOf(parentType, edge.factoryMethod()));
    return asTypeElements(factoryMethodType.getParameterTypes());
  }

  private Set<TypeElement> ownedModules(ComponentNode component, BindingGraph graph) {
    return Util.difference(
        ((ComponentNodeImpl) component).componentDescriptor().moduleTypes(),
        inheritedModules(component, graph));
  }

  private Set<TypeElement> inheritedModules(ComponentNode component, BindingGraph graph) {
    return Util.reentrantComputeIfAbsent(
        inheritedModulesCache, component, uncachedInheritedModules(graph));
  }

  private Function<ComponentNode, Set<TypeElement>> uncachedInheritedModules(BindingGraph graph) {
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
      Set<TypeElement> missingModules,
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
            .getQualifiedName(),
        missingModules.stream().map(TypeElement::toString).collect(Collectors.joining(", ")));
  }
}
