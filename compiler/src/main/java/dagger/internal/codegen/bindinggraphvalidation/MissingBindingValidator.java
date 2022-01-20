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

import static dagger.internal.codegen.base.Keys.isValidImplicitProvisionKey;
import static javax.tools.Diagnostic.Kind.ERROR;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.MissingBinding;
import dagger.model.Key;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import jakarta.inject.Inject;
import javax.lang.model.type.TypeKind;

/** Reports errors for missing bindings. */
final class MissingBindingValidator implements BindingGraphPlugin {

  private final DaggerTypes types;

  @Inject
  MissingBindingValidator(DaggerTypes types) {
    this.types = types;
  }

  @Override
  public String pluginName() {
    return "Dagger/MissingBinding";
  }

  @Override
  public void visitGraph(BindingGraph graph, DiagnosticReporter diagnosticReporter) {
    // Don't report missing bindings when validating a full binding graph or a graph built from a
    // subcomponent.
    if (graph.isFullBindingGraph() || graph.rootComponentNode().isSubcomponent()) {
      return;
    }
    graph
        .missingBindings()
        .forEach(missingBinding -> reportMissingBinding(missingBinding, graph, diagnosticReporter));
  }

  private void reportMissingBinding(
      MissingBinding missingBinding, BindingGraph graph, DiagnosticReporter diagnosticReporter) {
    diagnosticReporter.reportBinding(
        ERROR, missingBinding, missingBindingErrorMessage(missingBinding, graph));
  }

  private String missingBindingErrorMessage(MissingBinding missingBinding, BindingGraph graph) {
    Key key = missingBinding.key();
    StringBuilder errorMessage = new StringBuilder();
    // Wildcards should have already been checked by DependencyRequestValidator.
    Preconditions.checkState(!key.type().getKind().equals(TypeKind.WILDCARD), "unexpected wildcard request: %s", key);
    // TODO(ronshapiro): replace "provided" with "satisfied"?
    errorMessage.append(key).append(" cannot be provided without ");
    if (isValidImplicitProvisionKey(key, types)) {
      errorMessage.append("an @Inject constructor or ");
    }
    errorMessage.append("a @Provides-");
    errorMessage.append("annotated method.");
    graph.bindings(key).stream()
        .map(binding -> binding.componentPath().currentComponent())
        .distinct()
        .forEach(
            component ->
                errorMessage
                    .append("\nA binding with matching key exists in component: ")
                    .append(component.getQualifiedName()));
    return errorMessage.toString();
  }
}
