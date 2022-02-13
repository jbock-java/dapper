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
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.binding.InjectBindingRegistry;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions;
import dagger.internal.codegen.validation.BindingGraphPlugins;
import dagger.internal.codegen.xprocessing.XMessager;
import io.jbock.auto.common.BasicAnnotationProcessor;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;

/**
 * Init code moved away from ComponentProcessor, so we don't need members injection.
 */
public class ComponentProcessorHelper {

  private final InjectBindingRegistry injectBindingRegistry;
  private final SourceFileGenerator<ProvisionBinding> factoryGenerator;
  private final List<BasicAnnotationProcessor.Step> processingSteps;
  private final BindingGraphPlugins bindingGraphPlugins;
  private final Set<ClearableCache> clearableCaches;
  private final XMessager messager;

  @Inject
  ComponentProcessorHelper(
      InjectBindingRegistry injectBindingRegistry,
      SourceFileGenerator<ProvisionBinding> factoryGenerator,
      List<BasicAnnotationProcessor.Step> processingSteps,
      BindingGraphPlugins bindingGraphPlugins,
      Set<ClearableCache> clearableCaches,
      XMessager messager) {
    this.injectBindingRegistry = injectBindingRegistry;
    this.factoryGenerator = factoryGenerator;
    this.processingSteps = processingSteps;
    this.bindingGraphPlugins = bindingGraphPlugins;
    this.clearableCaches = clearableCaches;
    this.messager = messager;
  }

  Set<String> getSupportedOptions() {
    return Util.union(
        ProcessingEnvironmentCompilerOptions.supportedOptions(),
        bindingGraphPlugins.allSupportedOptions());
  }

  Iterable<? extends BasicAnnotationProcessor.Step> steps() {
    bindingGraphPlugins.initializePlugins();
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
