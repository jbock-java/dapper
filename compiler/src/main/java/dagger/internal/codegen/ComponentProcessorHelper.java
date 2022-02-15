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

package dagger.internal.codegen;

import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.SourceFileGenerationException;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.InjectBindingRegistry;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions;
import dagger.internal.codegen.validation.ExternalBindingGraphPlugins;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugins;
import dagger.internal.codegen.xprocessing.JavacBasicAnnotationProcessor;
import dagger.internal.codegen.xprocessing.XMessager;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;

/**
 * Init code moved away from ComponentProcessor, so we don't need members injection.
 */
public class ComponentProcessorHelper {

  private final InjectBindingRegistry injectBindingRegistry;
  private final SourceFileGenerator<ProvisionBinding> factoryGenerator;
  private final List<JavacBasicAnnotationProcessor.Step> processingSteps;
  private final ValidationBindingGraphPlugins validationBindingGraphPlugins;
  private final ExternalBindingGraphPlugins externalBindingGraphPlugins;
  private final Set<ClearableCache> clearableCaches;
  private final XMessager messager;

  @Inject
  ComponentProcessorHelper(
      InjectBindingRegistry injectBindingRegistry,
      SourceFileGenerator<ProvisionBinding> factoryGenerator,
      List<JavacBasicAnnotationProcessor.Step> processingSteps,
      ValidationBindingGraphPlugins validationBindingGraphPlugins,
      ExternalBindingGraphPlugins externalBindingGraphPlugins,
      Set<ClearableCache> clearableCaches,
      XMessager messager) {
    this.injectBindingRegistry = injectBindingRegistry;
    this.factoryGenerator = factoryGenerator;
    this.processingSteps = processingSteps;
    this.validationBindingGraphPlugins = validationBindingGraphPlugins;
    this.externalBindingGraphPlugins = externalBindingGraphPlugins;
    this.clearableCaches = clearableCaches;
    this.messager = messager;
  }

  Set<String> getSupportedOptions() {
    ArrayList<String> result = new ArrayList<>();
    result.addAll(ProcessingEnvironmentCompilerOptions.supportedOptions());
    result.addAll(validationBindingGraphPlugins.allSupportedOptions());
    result.addAll(externalBindingGraphPlugins.allSupportedOptions());
    return new LinkedHashSet<>(result);
  }

  Iterable<? extends JavacBasicAnnotationProcessor.Step> steps() {
    validationBindingGraphPlugins.initializePlugins();
    externalBindingGraphPlugins.initializePlugins();
    return processingSteps;
  }

  void postRound(RoundEnvironment roundEnv) {
    if (!roundEnv.processingOver()) {
      try {
        injectBindingRegistry.generateSourcesForRequiredBindings(
            factoryGenerator);
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(messager.toJavac());
      }
    }
    clearableCaches.forEach(ClearableCache::clearCache);
  }
}
