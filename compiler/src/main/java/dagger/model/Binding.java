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

package dagger.model;

import dagger.model.BindingGraph.MaybeBinding;
import dagger.spi.model.Key;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * The association between a {@link Key} and the way in which instances of the key are provided.
 * Includes any {@linkplain DependencyRequest dependencies} that are needed in order to provide the
 * instances.
 *
 * <p>If a binding is owned by more than one component, there is one {@code Binding} for every
 * owning component.
 */
public interface Binding extends MaybeBinding {
  @Override
  ComponentPath componentPath();

  /** @deprecated This always returns {@code Optional.of(this)}. */
  @Override
  @Deprecated
  default Optional<Binding> binding() {
    return Optional.of(this);
  }

  /**
   * The dependencies of this binding. The order of the dependencies corresponds to the order in
   * which they will be injected when the binding is requested.
   */
  Set<DependencyRequest> dependencies();

  /**
   * The {@link Element} that declares this binding. Absent for {@linkplain BindingKind binding
   * kinds} that are not always declared by exactly one element.
   */
  Optional<Element> bindingElement();

  /**
   * The {@link TypeElement} of the module which contributes this binding. Absent for bindings that
   * have no {@link #bindingElement() binding element}.
   */
  Optional<TypeElement> contributingModule();

  /**
   * Returns {@code true} if using this binding requires an instance of the {@link
   * #contributingModule()}.
   */
  boolean requiresModuleInstance();

  /** The scope of this binding if it has one. */
  Optional<Scope> scope();

  /** The kind of binding this instance represents. */
  BindingKind kind();
}
