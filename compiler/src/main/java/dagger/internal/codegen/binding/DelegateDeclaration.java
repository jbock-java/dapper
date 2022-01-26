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

import static java.util.Objects.requireNonNull;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import dagger.Binds;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import dagger.spi.model.Key;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;

/** The declaration for a delegate binding established by a {@link Binds} method. */
public final class DelegateDeclaration extends BindingDeclaration {

  private final Key key;
  private final Optional<Element> bindingElement;
  private final Optional<TypeElement> contributingModule;
  private final DependencyRequest delegateRequest;
  private final IntSupplier hash = Suppliers.memoizeInt(() ->
      Objects.hash(
          key(),
          bindingElement(),
          contributingModule(),
          delegateRequest()));

  DelegateDeclaration(
      Key key,
      Optional<Element> bindingElement,
      Optional<TypeElement> contributingModule,
      DependencyRequest delegateRequest) {
    this.key = requireNonNull(key);
    this.bindingElement = requireNonNull(bindingElement);
    this.contributingModule = requireNonNull(contributingModule);
    this.delegateRequest = requireNonNull(delegateRequest);
  }

  @Override
  public Key key() {
    return key;
  }

  @Override
  public Optional<Element> bindingElement() {
    return bindingElement;
  }

  @Override
  public Optional<TypeElement> contributingModule() {
    return contributingModule;
  }

  DependencyRequest delegateRequest() {
    return delegateRequest;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DelegateDeclaration that = (DelegateDeclaration) o;
    return hashCode() == that.hashCode()
        && key.equals(that.key)
        && bindingElement.equals(that.bindingElement)
        && contributingModule.equals(that.contributingModule)
        && delegateRequest.equals(that.delegateRequest);
  }

  @Override
  public int hashCode() {
    return hash.getAsInt();
  }

  /** A {@link DelegateDeclaration} factory. */
  public static final class Factory {
    private final DaggerTypes types;
    private final KeyFactory keyFactory;
    private final DependencyRequestFactory dependencyRequestFactory;

    @Inject
    Factory(
        DaggerTypes types,
        KeyFactory keyFactory,
        DependencyRequestFactory dependencyRequestFactory) {
      this.types = types;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    public DelegateDeclaration create(
        ExecutableElement bindsMethod, TypeElement contributingModule) {
      Preconditions.checkArgument(MoreElements.isAnnotationPresent(bindsMethod, Binds.class));
      ExecutableType resolvedMethod =
          MoreTypes.asExecutable(
              types.asMemberOf(MoreTypes.asDeclared(contributingModule.asType()), bindsMethod));
      DependencyRequest delegateRequest =
          dependencyRequestFactory.forRequiredResolvedVariable(
              Util.getOnlyElement(bindsMethod.getParameters()),
              Util.getOnlyElement(resolvedMethod.getParameterTypes()));
      return new DelegateDeclaration(
          keyFactory.forBindsMethod(bindsMethod, contributingModule),
          Optional.of(bindsMethod),
          Optional.of(contributingModule),
          delegateRequest);
    }
  }
}
