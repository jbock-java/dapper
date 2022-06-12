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

import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions;
import dagger.internal.codegen.xprocessing.JavacBasicAnnotationProcessor;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XProcessingStep;
import dagger.internal.codegen.xprocessing.XRoundEnv;
import dagger.spi.model.BindingGraphPlugin;
import java.util.Arrays;
import java.util.Optional;
import javax.lang.model.SourceVersion;

/**
 * The Javac annotation processor responsible for generating the classes that drive the Dagger
 * implementation.
 */
public final class ComponentProcessor extends JavacBasicAnnotationProcessor {
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
    return new ComponentProcessor(Optional.of(ImmutableSet.copyOf(testingPlugins)));
  }

  private final DelegateComponentProcessor delegate = new DelegateComponentProcessor();
  private final Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins;

  public ComponentProcessor() {
    this(Optional.empty());
  }

  private ComponentProcessor(Optional<ImmutableSet<BindingGraphPlugin>> testingPlugins) {
    super(options -> DelegateComponentProcessor.PROCESSING_ENV_CONFIG);
    this.testingPlugins = testingPlugins;
  }

  @Override
  public void initialize(XProcessingEnv env) {
    delegate.initialize(env, testingPlugins.orElseGet(() -> loadExternalPlugins(env)));
  }

  private static ImmutableSet<BindingGraphPlugin> loadExternalPlugins(XProcessingEnv env) {
    return ServiceLoaders.loadServices(env, BindingGraphPlugin.class);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public ImmutableSet<String> getSupportedOptions() {
    return ImmutableSet.<String>builder()
        .addAll(ProcessingEnvironmentCompilerOptions.supportedOptions())
        .addAll(delegate.validationBindingGraphPlugins.allSupportedOptions())
        .addAll(delegate.externalBindingGraphPlugins.allSupportedOptions())
        .build();
  }

  @Override
  public Iterable<XProcessingStep> processingSteps() {
    return delegate.processingSteps();
  }

  @Override
  public void postRound(XProcessingEnv env, XRoundEnv roundEnv) {
    delegate.postRound(env, roundEnv);
  }
}
