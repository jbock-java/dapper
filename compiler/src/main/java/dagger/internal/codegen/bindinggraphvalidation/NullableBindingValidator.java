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

import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;

import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.model.Binding;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import jakarta.inject.Inject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports errors or warnings (depending on the {@code -Adagger.nullableValidation} value) for each
 * non-nullable dependency request that is satisfied by a nullable binding.
 */
final class NullableBindingValidator implements BindingGraphPlugin {

  private final CompilerOptions compilerOptions;

  @Inject
  NullableBindingValidator(CompilerOptions compilerOptions) {
    this.compilerOptions = compilerOptions;
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    for (dagger.model.Binding binding : nullableBindings(bindingGraph)) {
      for (DependencyEdge dependencyEdge : nonNullableDependencies(bindingGraph, binding)) {
        diagnosticReporter.reportDependency(
            compilerOptions.nullableValidationKind(),
            dependencyEdge,
            nullableToNonNullable(
                binding.key().toString(),
                binding.toString())); // binding.toString() will include the @Nullable
      }
    }
  }

  @Override
  public String pluginName() {
    return "Dagger/Nullable";
  }

  private List<Binding> nullableBindings(BindingGraph bindingGraph) {
    return bindingGraph.bindings().stream()
        .filter(Binding::isNullable)
        .collect(Collectors.toList());
  }

  private Set<DependencyEdge> nonNullableDependencies(
      BindingGraph bindingGraph, dagger.model.Binding binding) {
    return bindingGraph.network().inEdges(binding).stream()
        .flatMap(instancesOf(DependencyEdge.class))
        .filter(edge -> !edge.dependencyRequest().isNullable())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  // Visible for testing
  static String nullableToNonNullable(String key, String binding) {
    return String.format("%s is not nullable, but is being provided by %s", key, binding);
  }
}
