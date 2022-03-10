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

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.collect.ImmutableMap;
import io.jbock.javapoet.ClassName;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Binds each {@link BindingMethodValidator} into a map, keyed by {@link
 * BindingMethodValidator#methodAnnotation()}.
 */
@Module
public interface BindingMethodValidatorsModule {

  @Provides
  static ImmutableMap<ClassName, BindingMethodValidator> indexValidators(
      ProvidesMethodValidator providesMethodValidator,
      BindsMethodValidator bindsMethodValidator,
      MultibindsMethodValidator multibindsMethodValidator,
      BindsOptionalOfMethodValidator bindsOptionalOfMethodValidator) {
    Map<ClassName, BindingMethodValidator> result = new LinkedHashMap<>();
    Stream.of(
            providesMethodValidator,
            bindsMethodValidator,
            multibindsMethodValidator,
            bindsOptionalOfMethodValidator)
        .forEach(v -> result.put(v.methodAnnotation(), v));
    return ImmutableMap.copyOf(result);
  }

  @Binds
  Map<ClassName, BindingMethodValidator> bindIndexValidators(
      ImmutableMap<ClassName, BindingMethodValidator> validators);
}
