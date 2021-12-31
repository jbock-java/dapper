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

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.SourceFileGenerationException;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.InjectBindingRegistry;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions;
import dagger.internal.codegen.validation.BindingGraphPlugins;
import jakarta.inject.Inject;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;

/**
 * Init code moved away from ComponentProcessor so we don't need members injection.
 */
public class ComponentProcessorHelper {

  private final InjectBindingRegistry injectBindingRegistry;
  private final SourceFileGenerator<ProvisionBinding> factoryGenerator;
  private final SourceFileGenerator<MembersInjectionBinding> membersInjectorGenerator;
  private final ImmutableList<BasicAnnotationProcessor.Step> processingSteps;
  private final BindingGraphPlugins bindingGraphPlugins;
  private final Set<ClearableCache> clearableCaches;
  private final Messager messager;

  @Inject
  ComponentProcessorHelper(
      InjectBindingRegistry injectBindingRegistry,
      SourceFileGenerator<ProvisionBinding> factoryGenerator,
      SourceFileGenerator<MembersInjectionBinding> membersInjectorGenerator,
      ImmutableList<BasicAnnotationProcessor.Step> processingSteps,
      BindingGraphPlugins bindingGraphPlugins,
      Set<ClearableCache> clearableCaches,
      Messager messager) {
    this.injectBindingRegistry = injectBindingRegistry;
    this.factoryGenerator = factoryGenerator;
    this.membersInjectorGenerator = membersInjectorGenerator;
    this.processingSteps = processingSteps;
    this.bindingGraphPlugins = bindingGraphPlugins;
    this.clearableCaches = clearableCaches;
    this.messager = messager;
  }

  Set<String> getSupportedOptions() {
    return Sets.union(
            ProcessingEnvironmentCompilerOptions.supportedOptions(),
            bindingGraphPlugins.allSupportedOptions())
        .immutableCopy();
  }

  Iterable<? extends BasicAnnotationProcessor.Step> steps() {
    bindingGraphPlugins.initializePlugins();
    return processingSteps;
  }

  void postRound(RoundEnvironment roundEnv) {
    if (!roundEnv.processingOver()) {
      try {
        injectBindingRegistry.generateSourcesForRequiredBindings(
            factoryGenerator, membersInjectorGenerator);
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(messager);
      }
    }
    clearableCaches.forEach(ClearableCache::clearCache);
  }
}
