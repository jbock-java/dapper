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

import dagger.Binds;
import dagger.Module;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.writing.FactoryGenerator;
import dagger.internal.codegen.writing.MembersInjectorGenerator;
import dagger.internal.codegen.writing.ModuleGenerator;
import dagger.internal.codegen.writing.ModuleProxies.ModuleConstructorProxyGenerator;

import javax.lang.model.element.TypeElement;

@Module
interface SourceFileGeneratorsModule {

  @Binds
  SourceFileGenerator<ProvisionBinding> factoryGenerator(FactoryGenerator generator);

  @Binds
  SourceFileGenerator<MembersInjectionBinding> membersInjectorGenerator(MembersInjectorGenerator generator);

  @Binds
  @ModuleGenerator
  SourceFileGenerator<TypeElement> moduleConstructorProxyGenerator(ModuleConstructorProxyGenerator generator);
}
