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

package dagger.internal.codegen;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.validation.BindingGraphValidator;
import dagger.spi.model.BindingGraphPlugin;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

/** Contains the bindings for {@link BindingGraphValidator} from external SPI providers. */
@Module
abstract class SpiModule {
  private SpiModule() {
  }

  @Provides
  @Singleton
  static Set<BindingGraphPlugin> externalPlugins(
      @TestingPlugins Optional<Set<BindingGraphPlugin>> testingPlugins,
      @ProcessorClassLoader ClassLoader processorClassLoader) {
    return testingPlugins.orElseGet(
        () ->
            Util.setOf(
                ServiceLoader.load(BindingGraphPlugin.class, processorClassLoader)));
  }

  @Qualifier
  @Retention(RUNTIME)
  @Target({FIELD, PARAMETER, METHOD})
  @interface TestingPlugins {
  }

  @Qualifier
  @Retention(RUNTIME)
  @Target({PARAMETER, METHOD})
  @interface ProcessorClassLoader {
  }
}
