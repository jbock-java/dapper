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

import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.validation.CompositeBindingGraphPlugin;
import dagger.internal.codegen.validation.Validation;
import dagger.spi.BindingGraphPlugin;
import java.util.Set;

/** Binds the set of {@link BindingGraphPlugin}s used to implement Dagger validation. */
@Module
public interface BindingGraphValidationModule {

  @Provides
  @Validation
  static Set<BindingGraphPlugin> providePlugins(
      CompositeBindingGraphPlugin.Factory factory,
      CompilerOptions compilerOptions,
      DependencyCycleValidator validation1,
      DuplicateBindingsValidator validation3,
      IncompatiblyScopedBindingsValidator validation4,
      InjectBindingValidator validation5,
      MapMultibindingValidator validation6,
      MissingBindingValidator validation7,
      NullableBindingValidator validation8,
      SetMultibindingValidator validation10,
      SubcomponentFactoryMethodValidator validation11) {
    Set<BindingGraphPlugin> plugins = Set.of(
        validation1,
        validation3,
        validation4,
        validation5,
        validation6,
        validation7,
        validation8,
        validation10,
        validation11);
    if (compilerOptions.experimentalDaggerErrorMessages()) {
      return Set.of(factory.create(plugins, "Dagger/Validation"));
    } else {
      return plugins;
    }
  }
}
