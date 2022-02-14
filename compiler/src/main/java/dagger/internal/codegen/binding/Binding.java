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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.base.Suppliers.memoize;
import static dagger.internal.codegen.binding.FrameworkType.PROVIDER;
import static io.jbock.auto.common.MoreTypes.asDeclared;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.STATIC;

import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.spi.model.BindingKind;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Scope;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * An abstract type for classes representing a Dagger binding. Particularly, contains the {@link
 * Element} that generated the binding and the {@link DependencyRequest} instances that are required
 * to satisfy the binding, but leaves the specifics of the <i>mechanism</i> of the binding to the
 * subtypes.
 */
public abstract class Binding extends BindingDeclaration {

  /**
   * Returns {@code true} if using this binding requires an instance of the {@link
   * #contributingModule()}.
   */
  public boolean requiresModuleInstance() {
    if (bindingElement().isEmpty() || contributingModule().isEmpty()) {
      return false;
    }
    Set<Modifier> modifiers = bindingElement().get().getModifiers();
    return !modifiers.contains(ABSTRACT) && !modifiers.contains(STATIC);
  }

  /** The kind of binding this instance represents. */
  public abstract BindingKind kind();

  /** The {@link BindingType} of this binding. */
  public abstract BindingType bindingType();

  /** The {@link FrameworkType} of this binding. */
  public final FrameworkType frameworkType() {
    return PROVIDER;
  }

  /**
   * The explicit set of {@link DependencyRequest dependencies} required to satisfy this binding as
   * defined by the user-defined injection sites.
   */
  public abstract Set<DependencyRequest> explicitDependencies();

  private final Supplier<Set<DependencyRequest>> dependencies =
      memoize(this::explicitDependencies);

  /**
   * The set of {@link DependencyRequest dependencies} required to satisfy this binding.
   */
  public final Set<DependencyRequest> dependencies() {
    return dependencies.get();
  }

  /**
   * If this binding's key's type parameters are different from those of the {@link
   * #bindingTypeElement()}, this is the binding for the {@link #bindingTypeElement()}'s unresolved
   * type.
   */
  public abstract Optional<? extends Binding> unresolved();

  public Optional<Scope> scope() {
    return Optional.empty();
  }

  // TODO(sameb): Remove the TypeElement parameter and pull it from the TypeMirror.
  static boolean hasNonDefaultTypeParameters(
      TypeElement element, TypeMirror type, DaggerTypes types) {
    // If the element has no type parameters, nothing can be wrong.
    if (element.getTypeParameters().isEmpty()) {
      return false;
    }

    List<TypeMirror> defaultTypes =
        element.getTypeParameters().stream()
            .map(TypeParameterElement::asType)
            .collect(Collectors.toList());

    List<TypeMirror> actualTypes =
        type.getKind() == TypeKind.DECLARED
            ? List.copyOf(asDeclared(type).getTypeArguments())
            : List.of();

    // The actual type parameter size can be different if the user is using a raw type.
    if (defaultTypes.size() != actualTypes.size()) {
      return true;
    }

    for (int i = 0; i < defaultTypes.size(); i++) {
      if (!types.isSameType(defaultTypes.get(i), actualTypes.get(i))) {
        return true;
      }
    }
    return false;
  }
}
