/*
 * Copyright (C) 2015 The Dagger Authors.
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
import static java.util.Objects.requireNonNull;

import com.google.auto.common.MoreTypes;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.ContributionType.HasContributionType;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Key;
import dagger.multibindings.Multibinds;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/**
 * A declaration that a multibinding with a certain key is available to be injected in a component
 * even if the component has no multibindings for that key. Identified by a map- or set-returning
 * method annotated with {@link Multibinds @Multibinds}.
 */
public final class MultibindingDeclaration extends BindingDeclaration
    implements HasContributionType {

  private final Optional<Element> bindingElement;
  private final Optional<TypeElement> contributingModule;
  private final Key key;
  private final ContributionType contributionType;
  private final IntSupplier hash = Suppliers.memoizeInt(() ->
      Objects.hash(bindingElement(), contributingModule(), key(), contributionType()));

  MultibindingDeclaration(
      Optional<Element> bindingElement,
      Optional<TypeElement> contributingModule,
      Key key,
      ContributionType contributionType) {
    this.bindingElement = requireNonNull(bindingElement);
    this.contributingModule = requireNonNull(contributingModule);
    this.key = requireNonNull(key);
    this.contributionType = requireNonNull(contributionType);
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
   * The map or set key whose availability is declared. For maps, this will be {@code Map<K,
   * Provider<V>>}. For sets, this will be {@code Set<T>}.
   */
  @Override
  public Key key() {
    return key;
  }

  /**
   * {@link ContributionType#SET} if the declared type is a {@link Set}, or
   * {@link ContributionType#MAP} if it is a {@link Map}.
   */
  @Override
  public ContributionType contributionType() {
    return contributionType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MultibindingDeclaration that = (MultibindingDeclaration) o;
    return hashCode() == that.hashCode()
        && bindingElement.equals(that.bindingElement)
        && contributingModule.equals(that.contributingModule)
        && key.equals(that.key)
        && contributionType == that.contributionType;
  }

  @Override
  public int hashCode() {
    return hash.getAsInt();
  }

  /** A factory for {@link MultibindingDeclaration}s. */
  public static final class Factory {
    private final DaggerTypes types;
    private final KeyFactory keyFactory;

    @Inject
    Factory(DaggerTypes types, KeyFactory keyFactory) {
      this.types = types;
      this.keyFactory = keyFactory;
    }

    /** A multibinding declaration for a {@link Multibinds @Multibinds} method. */
    MultibindingDeclaration forMultibindsMethod(
        ExecutableElement moduleMethod, TypeElement moduleElement) {
      Preconditions.checkArgument(isAnnotationPresent(moduleMethod, Multibinds.class));
      return forDeclaredMethod(
          moduleMethod,
          MoreTypes.asExecutable(
              types.asMemberOf(MoreTypes.asDeclared(moduleElement.asType()), moduleMethod)),
          moduleElement);
    }

    private MultibindingDeclaration forDeclaredMethod(
        ExecutableElement method,
        ExecutableType methodType,
        TypeElement contributingType) {
      TypeMirror returnType = methodType.getReturnType();
      Preconditions.checkArgument(
          SetType.isSet(returnType) || MapType.isMap(returnType),
          "%s must return a set or map",
          method);
      return new MultibindingDeclaration(
          Optional.of(method),
          Optional.of(contributingType),
          keyFactory.forMultibindsMethod(methodType, method),
          contributionType(returnType));
    }

    private ContributionType contributionType(TypeMirror returnType) {
      if (MapType.isMap(returnType)) {
        return ContributionType.MAP;
      } else if (SetType.isSet(returnType)) {
        return ContributionType.SET;
      } else {
        throw new IllegalArgumentException("Must be Map or Set: " + returnType);
      }
    }
  }
}
