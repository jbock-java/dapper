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

import static dagger.internal.codegen.base.Preconditions.checkArgument;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XAnnotation;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XMethodType;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.DaggerType;
import dagger.spi.model.Key;
import dagger.spi.model.Key.MultibindingContributionIdentifier;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.Optional;

/** A factory for {@code Key}s. */
public final class KeyFactory {
  private final XProcessingEnv processingEnv;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  KeyFactory(XProcessingEnv processingEnv, InjectionAnnotations injectionAnnotations) {
    this.processingEnv = processingEnv;
    this.injectionAnnotations = injectionAnnotations;
  }

  Key forComponentMethod(XMethodElement componentMethod) {
    return forMethod(componentMethod, componentMethod.getReturnType());
  }

  Key forSubcomponentCreatorMethod(
      XMethodElement subcomponentCreatorMethod, XType declaredContainer) {
    checkArgument(isDeclared(declaredContainer));
    XMethodType resolvedMethod = subcomponentCreatorMethod.asMemberOf(declaredContainer);
    return Key.builder(DaggerType.from(resolvedMethod.getReturnType())).build();
  }

  public Key forSubcomponentCreator(XType creatorType) {
    return Key.builder(DaggerType.from(creatorType)).build();
  }

  public Key forProvidesMethod(XMethodElement method, XTypeElement contributingModule) {
    return forBindingMethod(method, contributingModule, Optional.of(TypeNames.PROVIDER));
  }

  /** Returns the key bound by a {@code Binds} method. */
  Key forBindsMethod(XMethodElement method, XTypeElement contributingModule) {
    checkArgument(method.hasAnnotation(TypeNames.BINDS));
    return forBindingMethod(method, contributingModule, Optional.empty());
  }

  private Key forBindingMethod(
      XMethodElement method,
      XTypeElement contributingModule,
      Optional<ClassName> frameworkClassName) {
    XMethodType methodType = method.asMemberOf(contributingModule.getType());
    ContributionType contributionType = ContributionType.fromBindingElement(method);
    XType returnType = methodType.getReturnType();
    XType keyType = bindingMethodKeyType(returnType, method, contributionType, frameworkClassName);
    Key key = forMethod(method, keyType);
    return contributionType.equals(ContributionType.UNIQUE)
        ? key
        : key.toBuilder()
            .multibindingContributionIdentifier(
                new MultibindingContributionIdentifier(method, contributingModule))
            .build();
  }

  private XType bindingMethodKeyType(
      XType returnType,
      XMethodElement method,
      ContributionType contributionType,
      Optional<ClassName> frameworkClassName) {
    switch (contributionType) {
      case UNIQUE:
        return returnType;
    }
    throw new AssertionError();
  }

  /**
   * Returns the key for a binding associated with a {@code DelegateDeclaration}.
   *
   * <p>If {@code delegateDeclaration} is {@code @IntoMap}, transforms the {@code Map<K, V>} key
   * from {@code DelegateDeclaration#key()} to {@code Map<K, FrameworkType<V>>}. If {@code
   * delegateDeclaration} is not a map contribution, its key is returned.
   */
  Key forDelegateBinding(DelegateDeclaration delegateDeclaration, ClassName frameworkType) {
    return delegateDeclaration.key();
  }

  private Key forMethod(XMethodElement method, XType keyType) {
    return forQualifiedType(injectionAnnotations.getQualifier(method), keyType);
  }

  public Key forInjectConstructorWithResolvedType(XType type) {
    return Key.builder(DaggerType.from(type)).build();
  }

  // TODO(ronshapiro): Remove these conveniences which are simple wrappers around Key.Builder
  Key forType(XType type) {
    return Key.builder(DaggerType.from(type)).build();
  }

  public Key forMembersInjectedType(XType type) {
    return Key.builder(DaggerType.from(type)).build();
  }

  Key forQualifiedType(Optional<XAnnotation> qualifier, XType type) {
    return Key.builder(DaggerType.from(type.boxed()))
        .qualifier(qualifier.map(DaggerAnnotation::from))
        .build();
  }
}
