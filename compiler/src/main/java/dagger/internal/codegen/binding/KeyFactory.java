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
import static dagger.internal.codegen.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.langmodel.DaggerTypes.isFutureType;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.unwrapType;
import static io.jbock.auto.common.MoreTypes.isType;

import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.SetType;
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

  private XType setOf(XType elementType) {
    return processingEnv.getDeclaredType(
        processingEnv.requireTypeElement(TypeNames.SET), elementType.boxed());
  }

  private XType mapOf(XType keyType, XType valueType) {
    return processingEnv.getDeclaredType(
        processingEnv.requireTypeElement(TypeNames.MAP), keyType.boxed(), valueType.boxed());
  }

  /** Returns {@code Map<KeyType, FrameworkType<ValueType>>}. */
  private XType mapOfFrameworkType(XType keyType, ClassName frameworkClassName, XType valueType) {
    return mapOf(
        keyType,
        processingEnv.getDeclaredType(
            processingEnv.requireTypeElement(frameworkClassName), valueType.boxed()));
  }

  Key forComponentMethod(XMethodElement componentMethod) {
    return forMethod(componentMethod, componentMethod.getReturnType());
  }

  Key forProductionComponentMethod(XMethodElement componentMethod) {
    XType returnType = componentMethod.getReturnType();
    XType keyType =
        isFutureType(returnType) ? getOnlyElement(returnType.getTypeArguments()) : returnType;
    return forMethod(componentMethod, keyType);
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

  public Key forProducesMethod(XMethodElement method, XTypeElement contributingModule) {
    return forBindingMethod(method, contributingModule, Optional.of(TypeNames.PRODUCER));
  }

  /** Returns the key bound by a {@code Binds} method. */
  Key forBindsMethod(XMethodElement method, XTypeElement contributingModule) {
    checkArgument(method.hasAnnotation(TypeNames.BINDS));
    return forBindingMethod(method, contributingModule, Optional.empty());
  }

  /** Returns the base key bound by a {@code BindsOptionalOf} method. */
  Key forBindsOptionalOfMethod(XMethodElement method, XTypeElement contributingModule) {
    checkArgument(method.hasAnnotation(TypeNames.BINDS_OPTIONAL_OF));
    return forBindingMethod(method, contributingModule, Optional.empty());
  }

  private Key forBindingMethod(
      XMethodElement method,
      XTypeElement contributingModule,
      Optional<ClassName> frameworkClassName) {
    XMethodType methodType = method.asMemberOf(contributingModule.getType());
    ContributionType contributionType = ContributionType.fromBindingElement(method);
    XType returnType = methodType.getReturnType();
    if (frameworkClassName.isPresent()
        && frameworkClassName.get().equals(TypeNames.PRODUCER)
        && isType(toJavac(returnType))) {
      if (isFutureType(methodType.getReturnType())) {
        returnType = getOnlyElement(returnType.getTypeArguments());
      } else if (contributionType.equals(ContributionType.SET_VALUES)
          && SetType.isSet(returnType)) {
        SetType setType = SetType.from(returnType);
        if (isFutureType(setType.elementType())) {
          returnType = setOf(unwrapType(setType.elementType()));
        }
      }
    }
    XType keyType = bindingMethodKeyType(returnType, method, contributionType, frameworkClassName);
    Key key = forMethod(method, keyType);
    return contributionType.equals(ContributionType.UNIQUE)
        ? key
        : key.toBuilder()
            .multibindingContributionIdentifier(
                new MultibindingContributionIdentifier(method, contributingModule))
            .build();
  }

  /**
   * Returns the key for a {@code Multibinds @Multibinds} method.
   *
   * <p>The key's type is either {@code Set<T>} or {@code Map<K, Provider<V>>}. The latter works
   * even for maps used by {@code Producer}s.
   */
  Key forMultibindsMethod(XMethodElement method, XMethodType methodType) {
    XType returnType = method.getReturnType();
    XType keyType =
        MapType.isMap(returnType)
            ? mapOfFrameworkType(
                MapType.from(returnType).keyType(),
                TypeNames.PROVIDER,
                MapType.from(returnType).valueType())
            : returnType;
    return forMethod(method, keyType);
  }

  private XType bindingMethodKeyType(
      XType returnType,
      XMethodElement method,
      ContributionType contributionType,
      Optional<ClassName> frameworkClassName) {
    switch (contributionType) {
      case UNIQUE:
        return returnType;
      case SET:
        return setOf(returnType);
      case SET_VALUES:
        // TODO(gak): do we want to allow people to use "covariant return" here?
        checkArgument(SetType.isSet(returnType));
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
