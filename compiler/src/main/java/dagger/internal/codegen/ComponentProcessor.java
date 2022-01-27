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

import dagger.BindsInstance;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.SpiModule.TestingPlugins;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.bindinggraphvalidation.BindingGraphValidationModule;
import dagger.internal.codegen.componentgenerator.ComponentGeneratorModule;
import dagger.internal.codegen.validation.BindingMethodProcessingStep;
import dagger.internal.codegen.validation.BindingMethodValidatorsModule;
import dagger.internal.codegen.validation.BindsInstanceProcessingStep;
import dagger.internal.codegen.validation.InjectBindingRegistryModule;
import dagger.spi.BindingGraphPlugin;
import io.jbock.auto.common.BasicAnnotationProcessor;
import jakarta.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;

/**
 * The annotation processor responsible for generating the classes that drive the Dagger 2.0
 * implementation.
 *
 * <p>TODO(gak): give this some better documentation
 */
public class ComponentProcessor extends BasicAnnotationProcessor {
  private final Optional<Set<BindingGraphPlugin>> testingPlugins;

  private ComponentProcessorHelper helper;

  public ComponentProcessor() {
    this.testingPlugins = Optional.empty();
  }

  private ComponentProcessor(Iterable<BindingGraphPlugin> testingPlugins) {
    this.testingPlugins = Optional.of(Util.setOf(testingPlugins));
  }

  /**
   * Creates a component processor that uses given {@link BindingGraphPlugin}s instead of loading
   * them from a {@link java.util.ServiceLoader}.
   */
  // visible for testing
  public static ComponentProcessor forTesting(BindingGraphPlugin... testingPlugins) {
    return forTesting(Arrays.asList(testingPlugins));
  }

  /**
   * Creates a component processor that uses given {@link BindingGraphPlugin}s instead of loading
   * them from a {@link java.util.ServiceLoader}.
   */
  // visible for testing
  public static ComponentProcessor forTesting(Iterable<BindingGraphPlugin> testingPlugins) {
    return new ComponentProcessor(testingPlugins);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public Set<String> getSupportedOptions() {
    return helper.getSupportedOptions();
  }

  @Override
  protected Iterable<? extends Step> steps() {
    helper = ProcessorComponent.factory().create(processingEnv, testingPlugins).helper();
    return helper.steps();
  }

  @Singleton
  @Component(
      modules = {
          BindingGraphValidationModule.class,
          BindingMethodValidatorsModule.class,
          ComponentGeneratorModule.class,
          InjectBindingRegistryModule.class,
          ProcessingEnvironmentModule.class,
          ProcessingRoundCacheModule.class,
          ProcessingStepsModule.class,
          SourceFileGeneratorsModule.class,
          SpiModule.class
      })
  interface ProcessorComponent {
    ComponentProcessorHelper helper();

    static Factory factory() {
      return DaggerComponentProcessor_ProcessorComponent.factory();
    }

    @Component.Factory
    interface Factory {
      ProcessorComponent create(
          @BindsInstance ProcessingEnvironment processingEnv,
          @BindsInstance @TestingPlugins Optional<Set<BindingGraphPlugin>> testingPlugins);
    }
  }

  @Module
  interface ProcessingStepsModule {
    @Provides
    static List<Step> processingSteps(
        InjectProcessingStep injectProcessingStep,
        AssistedInjectProcessingStep assistedInjectProcessingStep,
        AssistedFactoryProcessingStep assistedFactoryProcessingStep,
        AssistedProcessingStep assistedProcessingStep,
        BindsInstanceProcessingStep bindsInstanceProcessingStep,
        ModuleProcessingStep moduleProcessingStep,
        ComponentProcessingStep componentProcessingStep,
        BindingMethodProcessingStep bindingMethodProcessingStep) {
      return List.of(
          injectProcessingStep,
          assistedInjectProcessingStep,
          assistedFactoryProcessingStep,
          assistedProcessingStep,
          bindsInstanceProcessingStep,
          moduleProcessingStep,
          componentProcessingStep,
          bindingMethodProcessingStep);
    }
  }

  @Override
  protected void postRound(RoundEnvironment roundEnv) {
    helper.postRound(roundEnv);
  }
}
