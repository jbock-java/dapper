/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen.binding;

import dagger.internal.codegen.base.SourceFileGenerationException;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.xprocessing.XConstructorElement;
import dagger.internal.codegen.xprocessing.XFieldElement;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.spi.model.Key;
import java.util.Optional;

/**
 * Maintains the collection of provision bindings from {@code Inject} constructors and members
 * injection bindings from {@code Inject} fields and methods known to the annotation processor. Note
 * that this registry <b>does not</b> handle any explicit bindings (those from {@code Provides}
 * methods, {@code Component} dependencies, etc.).
 */
public interface InjectBindingRegistry {
  /**
   * Returns a {@code ProvisionBinding} for {@code key}. If none has been registered yet, registers
   * one.
   */
  Optional<ProvisionBinding> getOrFindProvisionBinding(Key key);

  /**
   * Returns a {@code MembersInjectionBinding} for {@code key}. If none has been registered yet,
   * registers one, along with all necessary members injection bindings for superclasses.
   */
  Optional<MembersInjectionBinding> getOrFindMembersInjectionBinding(Key key);

  /**
   * Returns a {@code ProvisionBinding} for a {@code dagger.MembersInjector} of {@code key}. If none
   * has been registered yet, registers one.
   */
  Optional<ProvisionBinding> getOrFindMembersInjectorProvisionBinding(Key key);

  Optional<ProvisionBinding> tryRegisterInjectConstructor(XConstructorElement constructorElement);

  Optional<MembersInjectionBinding> tryRegisterInjectField(XFieldElement fieldElement);

  Optional<MembersInjectionBinding> tryRegisterInjectMethod(XMethodElement methodElement);

  /**
   * This method ensures that sources for all registered {@code Binding bindings} (either explicitly
   * or implicitly via {@code #getOrFindMembersInjectionBinding} or {@code
   * #getOrFindProvisionBinding}) are generated.
   */
  void generateSourcesForRequiredBindings(
      SourceFileGenerator<ProvisionBinding> factoryGenerator,
      SourceFileGenerator<MembersInjectionBinding> membersInjectorGenerator)
      throws SourceFileGenerationException;
}
