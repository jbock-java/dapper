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
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.SourceFileGenerationException;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.InjectBindingRegistry;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.bindinggraphvalidation.BindingGraphValidationModule;
import dagger.internal.codegen.collect.ImmutableList;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions;
import dagger.internal.codegen.componentgenerator.ComponentGeneratorModule;
import dagger.internal.codegen.validation.BindingMethodProcessingStep;
import dagger.internal.codegen.validation.BindingMethodValidatorsModule;
import dagger.internal.codegen.validation.BindsInstanceProcessingStep;
import dagger.internal.codegen.validation.ExternalBindingGraphPlugins;
import dagger.internal.codegen.validation.InjectBindingRegistryModule;
import dagger.internal.codegen.validation.MultibindingAnnotationsProcessingStep;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugins;
import dagger.internal.codegen.xprocessing.JavacBasicAnnotationProcessor;
import dagger.internal.codegen.xprocessing.XConverters;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XProcessingEnvConfig;
import dagger.internal.codegen.xprocessing.XProcessingStep;
import dagger.internal.codegen.xprocessing.XRoundEnv;
import dagger.spi.model.BindingGraphPlugin;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.SourceVersion;

/**
 * The annotation processor responsible for generating the classes that drive the Dagger 2.0
 * implementation.
 *
 * <p>TODO(gak): give this some better documentation
 */
public class ComponentProcessor extends JavacBasicAnnotationProcessor {
  private static XProcessingEnvConfig envConfig(Map<String, String> options) {
    return new XProcessingEnvConfig.Builder().disableAnnotatedElementValidation(true).build();
  }

  private final Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins;

  @Inject InjectBindingRegistry injectBindingRegistry;
  @Inject SourceFileGenerator<ProvisionBinding> factoryGenerator;
  @Inject SourceFileGenerator<MembersInjectionBinding> membersInjectorGenerator;
  @Inject ImmutableList<XProcessingStep> processingSteps;
  @Inject ValidationBindingGraphPlugins validationBindingGraphPlugins;
  @Inject ExternalBindingGraphPlugins externalBindingGraphPlugins;
  @Inject Set<ClearableCache> clearableCaches;

  public ComponentProcessor() {
    super(ComponentProcessor::envConfig);
    this.testingPlugins = Optional.empty();
  }

  private ComponentProcessor(Iterable<BindingGraphPlugin> testingPlugins) {
    super(ComponentProcessor::envConfig);
    this.testingPlugins = Optional.of(ImmutableSet.copyOf(testingPlugins));
  }

  /**
   * Creates a component processor that uses given {@code BindingGraphPlugin}s instead of loading
   * them from a {@code java.util.ServiceLoader}.
   */
  public static ComponentProcessor forTesting(BindingGraphPlugin... testingPlugins) {
    return forTesting(Arrays.asList(testingPlugins));
  }

  /**
   * Creates a component processor that uses given {@code BindingGraphPlugin}s instead of loading
   * them from a {@code java.util.ServiceLoader}.
   */
  public static ComponentProcessor forTesting(Iterable<BindingGraphPlugin> testingPlugins) {
    return new ComponentProcessor(testingPlugins);
  }

  @Override
  public void initialize(XProcessingEnv env) {
    ProcessorComponent.factory()
        .create(env, testingPlugins.orElseGet(this::loadExternalPlugins))
        .inject(this);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public ImmutableSet<String> getSupportedOptions() {
    return ImmutableSet.<String>builder()
        .addAll(ProcessingEnvironmentCompilerOptions.supportedOptions())
        .addAll(validationBindingGraphPlugins.allSupportedOptions())
        .addAll(externalBindingGraphPlugins.allSupportedOptions())
        .build();
  }

  @Override
  public Iterable<XProcessingStep> processingSteps() {
    validationBindingGraphPlugins.initializePlugins();
    externalBindingGraphPlugins.initializePlugins();

    return processingSteps;
  }

  private ImmutableSet<BindingGraphPlugin> loadExternalPlugins() {
    return ImmutableSet.of();
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
      })
  interface ProcessorComponent {
    void inject(ComponentProcessor processor);

    static Factory factory() {
      return DaggerComponentProcessor_ProcessorComponent.factory();
    }

    @Component.Factory
    interface Factory {
      ProcessorComponent create(
          @BindsInstance XProcessingEnv xProcessingEnv,
          @BindsInstance ImmutableSet<BindingGraphPlugin> externalPlugins);
    }
  }

  @Module
  interface ProcessingStepsModule {
    @Provides
    static ImmutableList<XProcessingStep> processingSteps(
        MapKeyProcessingStep mapKeyProcessingStep,
        InjectProcessingStep injectProcessingStep,
        AssistedInjectProcessingStep assistedInjectProcessingStep,
        AssistedFactoryProcessingStep assistedFactoryProcessingStep,
        AssistedProcessingStep assistedProcessingStep,
        MultibindingAnnotationsProcessingStep multibindingAnnotationsProcessingStep,
        BindsInstanceProcessingStep bindsInstanceProcessingStep,
        ModuleProcessingStep moduleProcessingStep,
        ComponentProcessingStep componentProcessingStep,
        BindingMethodProcessingStep bindingMethodProcessingStep,
        CompilerOptions compilerOptions) {
      return ImmutableList.of(
          mapKeyProcessingStep,
          injectProcessingStep,
          assistedInjectProcessingStep,
          assistedFactoryProcessingStep,
          assistedProcessingStep,
          multibindingAnnotationsProcessingStep,
          bindsInstanceProcessingStep,
          moduleProcessingStep,
          componentProcessingStep,
          bindingMethodProcessingStep);
    }
  }

  @Override
  public void postRound(XProcessingEnv env, XRoundEnv roundEnv) {
    // TODO(bcorso): Add a way to determine if processing is over without converting to Javac here.
    if (!XConverters.toJavac(roundEnv).processingOver()) {
      try {
        injectBindingRegistry.generateSourcesForRequiredBindings(
            factoryGenerator, membersInjectorGenerator);
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(env.getMessager());
      }
    }
    clearableCaches.forEach(ClearableCache::clearCache);
  }
}
