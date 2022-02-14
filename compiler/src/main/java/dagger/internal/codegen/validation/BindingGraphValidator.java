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

import static java.util.Objects.requireNonNull;
import static javax.tools.Diagnostic.Kind.ERROR;

import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.compileroption.ValidationType;
import dagger.internal.codegen.validation.DiagnosticReporterFactory.DiagnosticReporterImpl;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.model.BindingGraph;
import dagger.spi.BindingGraphPlugin;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Set;
import javax.lang.model.element.TypeElement;

/** Validates a {@link BindingGraph}. */
@Singleton
public final class BindingGraphValidator {
  private final Set<BindingGraphPlugin> validationPlugins;
  private final Set<BindingGraphPlugin> externalPlugins;
  private final DiagnosticReporterFactory diagnosticReporterFactory;
  private final CompilerOptions compilerOptions;

  @Inject
  BindingGraphValidator(
      @Validation Set<BindingGraphPlugin> validationPlugins,
      Set<BindingGraphPlugin> externalPlugins,
      DiagnosticReporterFactory diagnosticReporterFactory,
      CompilerOptions compilerOptions) {
    this.validationPlugins = validationPlugins;
    this.externalPlugins = externalPlugins;
    this.diagnosticReporterFactory = requireNonNull(diagnosticReporterFactory);
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
    return validate(graph) && visitPlugins(graph);
  }

  /** Returns {@code true} if validation plugins report no errors. */
  private boolean validate(BindingGraph graph) {
    if (graph.isFullBindingGraph() && !requiresFullBindingGraphValidation()) {
      return true;
    }

    boolean errorsAsWarnings =
        graph.isFullBindingGraph()
            && compilerOptions.fullBindingGraphValidationType().equals(ValidationType.WARNING);

    return runPlugins(validationPlugins, graph, errorsAsWarnings);
  }

  /** Returns {@code true} if external plugins report no errors. */
  private boolean visitPlugins(BindingGraph graph) {
    TypeElement component = graph.rootComponentNode().componentPath().currentComponent().java();
    if (graph.isFullBindingGraph()
        // TODO(b/135938915): Consider not visiting plugins if only
        // fullBindingGraphValidation is enabled.
        && !requiresFullBindingGraphValidation()
        && !compilerOptions.pluginsVisitFullBindingGraphs(component)) {
      return true;
    }
    return runPlugins(externalPlugins, graph, /*errorsAsWarnings=*/ false);
  }

  /** Returns {@code false} if any of the plugins reported an error. */
  private boolean runPlugins(
      Set<BindingGraphPlugin> plugins, BindingGraph graph, boolean errorsAsWarnings) {
    boolean isClean = true;
    for (BindingGraphPlugin plugin : plugins) {
      DiagnosticReporterImpl reporter =
          diagnosticReporterFactory.reporter(graph, plugin.pluginName(), errorsAsWarnings);
      plugin.visitGraph(graph, reporter);
      if (reporter.reportedDiagnosticKinds().contains(ERROR)) {
        isClean = false;
      }
    }
    return isClean;
  }
}
