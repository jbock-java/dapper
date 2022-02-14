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

import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.compileroption.ValidationType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.model.BindingGraph;
import dagger.spi.model.DaggerTypeElement;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Validates a {@link BindingGraph}. */
@Singleton
public final class BindingGraphValidator {
  private final ValidationBindingGraphPlugins validationPlugins;
  private final ExternalBindingGraphPlugins externalPlugins;
  private final CompilerOptions compilerOptions;

  @Inject
  BindingGraphValidator(
      ValidationBindingGraphPlugins validationPlugins,
      ExternalBindingGraphPlugins externalPlugins,
      CompilerOptions compilerOptions) {
    this.validationPlugins = validationPlugins;
    this.externalPlugins = externalPlugins;
    this.compilerOptions = compilerOptions;
  }

  /** Returns {@code true} if validation or analysis is required on the full binding graph. */
  public boolean shouldDoFullBindingGraphValidation(XTypeElement component) {
    return requiresFullBindingGraphValidation()
        || compilerOptions.pluginsVisitFullBindingGraphs(component.toJavac());
  }

  private boolean requiresFullBindingGraphValidation() {
    return !compilerOptions.fullBindingGraphValidationType().equals(ValidationType.NONE);
  }

  /** Returns {@code true} if no errors are reported for {@code graph}. */
  public boolean isValid(BindingGraph graph) {
    return visitValidationPlugins(graph) && visitExternalPlugins(graph);
  }

  /** Returns {@code true} if validation plugins report no errors. */
  private boolean visitValidationPlugins(BindingGraph graph) {
    if (graph.isFullBindingGraph() && !requiresFullBindingGraphValidation()) {
      return true;
    }

    return validationPlugins.visit(graph);
  }

  /** Returns {@code true} if external plugins report no errors. */
  private boolean visitExternalPlugins(BindingGraph graph) {
    DaggerTypeElement component = graph.rootComponentNode().componentPath().currentComponent();
    if (graph.isFullBindingGraph()
        // TODO(b/135938915): Consider not visiting plugins if only
        // fullBindingGraphValidation is enabled.
        && !requiresFullBindingGraphValidation()
        && !compilerOptions.pluginsVisitFullBindingGraphs(component.java())) {
      return true;
    }

    return externalPlugins.visit(graph);
  }
}
