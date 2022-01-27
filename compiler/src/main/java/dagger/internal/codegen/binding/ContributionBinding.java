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

import static dagger.internal.codegen.binding.ContributionBinding.FactoryCreationStrategy.CLASS_CONSTRUCTOR;
import static dagger.internal.codegen.binding.ContributionBinding.FactoryCreationStrategy.DELEGATE;
import static dagger.internal.codegen.binding.ContributionBinding.FactoryCreationStrategy.SINGLETON_INSTANCE;

import dagger.spi.model.Key;
import io.jbock.auto.common.MoreElements;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

/**
 * An abstract class for a value object representing the mechanism by which a {@link Key} can be
 * contributed to a dependency graph.
 */
public abstract class ContributionBinding extends Binding {

  /** If {@link #bindingElement()} is a method that returns a primitive type, returns that type. */
  public final Optional<TypeMirror> contributedPrimitiveType() {
    return bindingElement()
        .filter(bindingElement -> bindingElement instanceof ExecutableElement)
        .map(bindingElement -> MoreElements.asExecutable(bindingElement).getReturnType())
        .filter(type -> type.getKind().isPrimitive());
  }

  /** The strategy for getting an instance of a factory for a {@link ContributionBinding}. */
  public enum FactoryCreationStrategy {
    /** The factory class is a single instance. */
    SINGLETON_INSTANCE,
    /** The factory must be created by calling the constructor. */
    CLASS_CONSTRUCTOR,
    /** The factory is simply delegated to another. */
    DELEGATE,
  }

  /**
   * Returns the {@link FactoryCreationStrategy} appropriate for a binding.
   *
   * <p>Delegate bindings use the {@link FactoryCreationStrategy#DELEGATE} strategy.
   *
   * <p>Bindings without dependencies that don't require a module instance use the {@link
   * FactoryCreationStrategy#SINGLETON_INSTANCE} strategy.
   *
   * <p>All other bindings use the {@link FactoryCreationStrategy#CLASS_CONSTRUCTOR} strategy.
   */
  public final FactoryCreationStrategy factoryCreationStrategy() {
    switch (kind()) {
      case DELEGATE:
        return DELEGATE;
      case PROVISION:
        return dependencies().isEmpty() && !requiresModuleInstance()
            ? SINGLETON_INSTANCE
            : CLASS_CONSTRUCTOR;
      case INJECTION:
        return dependencies().isEmpty() ? SINGLETON_INSTANCE : CLASS_CONSTRUCTOR;
      default:
        return CLASS_CONSTRUCTOR;
    }
  }

  /**
   * The {@link TypeMirror type} for the {@code Factory<T>} which is created
   * for this binding.
   */
  public final TypeMirror contributedType() {
    return key().type().java();
  }
}
