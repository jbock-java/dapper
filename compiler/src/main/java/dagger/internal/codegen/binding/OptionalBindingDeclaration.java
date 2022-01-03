/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static dagger.internal.codegen.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import dagger.BindsOptionalOf;
import dagger.internal.codegen.base.Suppliers;
import dagger.model.Key;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** A {@link BindsOptionalOf} declaration. */
final class OptionalBindingDeclaration extends BindingDeclaration {

  private final Optional<Element> bindingElement;
  private final Optional<TypeElement> contributingModule;
  private final Key key;
  private final IntSupplier hash = Suppliers.memoizeInt(() ->
      Objects.hash(bindingElement(), contributingModule(), key()));

  OptionalBindingDeclaration(
      Optional<Element> bindingElement,
      Optional<TypeElement> contributingModule,
      Key key) {
    this.bindingElement = requireNonNull(bindingElement);
    this.contributingModule = requireNonNull(contributingModule);
    this.key = requireNonNull(key);
  }

  @Override
  public Optional<Element> bindingElement() {
    return bindingElement;
  }

  @Override
  public Optional<TypeElement> contributingModule() {
    return contributingModule;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The key's type is the method's return type, even though the synthetic bindings will be for
   * {@code Optional} of derived types.
   */
  @Override
  public Key key() {
    return key;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    OptionalBindingDeclaration that = (OptionalBindingDeclaration) o;
    return hashCode() == that.hashCode()
        && bindingElement.equals(that.bindingElement)
        && contributingModule.equals(that.contributingModule)
        && key.equals(that.key);
  }

  @Override
  public int hashCode() {
    return hash.getAsInt();
  }

  static class Factory {
    private final KeyFactory keyFactory;

    @Inject
    Factory(KeyFactory keyFactory) {
      this.keyFactory = keyFactory;
    }

    OptionalBindingDeclaration forMethod(ExecutableElement method, TypeElement contributingModule) {
      checkArgument(isAnnotationPresent(method, BindsOptionalOf.class));
      return new OptionalBindingDeclaration(
          Optional.of(method),
          Optional.of(contributingModule),
          keyFactory.forBindsOptionalOfMethod(method, contributingModule));
    }
  }
}
