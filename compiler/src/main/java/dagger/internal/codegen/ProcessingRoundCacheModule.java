/*
 * Copyright (C) 2019 The Dagger Authors.
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

import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.binding.BindingGraphFactory;
import dagger.internal.codegen.binding.ModuleDescriptor;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.validation.AnyBindingMethodValidator;
import dagger.internal.codegen.validation.ComponentCreatorValidator;
import dagger.internal.codegen.validation.ComponentValidator;
import dagger.internal.codegen.validation.SuperficialValidator;
import dagger.internal.codegen.validation.InjectValidator;
import java.util.Set;

/**
 * Binding contributions to a set of {@link ClearableCache}s that will be cleared at the end of each
 * processing round.
 */
@Module
interface ProcessingRoundCacheModule {

  @Provides
  static Set<ClearableCache> clearableCaches(
      AnyBindingMethodValidator anyBindingMethodValidator,
      InjectValidator injectValidator,
      ModuleDescriptor.Factory moduleDescriptorFactory,
      BindingGraphFactory bindingGraphFactory,
      ComponentValidator componentValidator,
      ComponentCreatorValidator componentCreatorValidator,
      SuperficialValidator superficialValidator,
      DaggerElements elements) {
    return Set.of(
        anyBindingMethodValidator,
        injectValidator,
        moduleDescriptorFactory,
        bindingGraphFactory,
        componentValidator,
        componentCreatorValidator,
        superficialValidator,
        elements);
  }
}
