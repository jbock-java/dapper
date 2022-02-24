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
import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;
import static dagger.internal.codegen.langmodel.DaggerTypes.isFutureType;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.xprocessing.XConverters.toXProcessing;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static javax.lang.model.element.ElementKind.METHOD;

import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
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
import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;

/** A factory for {@code Key}s. */
public final class KeyFactory {
  private final XProcessingEnv processingEnv;
  private final DaggerTypes types;
  private final DaggerElements elements;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  KeyFactory(
      XProcessingEnv processingEnv,
      DaggerTypes types,
      DaggerElements elements,
      InjectionAnnotations injectionAnnotations) {
    this.processingEnv = processingEnv;
    this.types = types;
    this.elements = elements;
    this.injectionAnnotations = injectionAnnotations;
  }

  private TypeMirror boxPrimitives(TypeMirror type) {
    return type.getKind().isPrimitive() ? types.boxedClass((PrimitiveType) type).asType() : type;
  }

  private DeclaredType setOf(TypeMirror elementType) {
    return types.getDeclaredType(
        elements.getTypeElement(TypeNames.SET), boxPrimitives(elementType));
  }

  private DeclaredType mapOf(XType keyType, XType valueType) {
    return mapOf(toJavac(keyType), toJavac(valueType));
  }

  private DeclaredType mapOf(TypeMirror keyType, TypeMirror valueType) {
    return types.getDeclaredType(
        elements.getTypeElement(TypeNames.MAP), boxPrimitives(keyType), boxPrimitives(valueType));
  }

  /** Returns {@code Map<KeyType, FrameworkType<ValueType>>}. */
  private TypeMirror mapOfFrameworkType(
      XType keyType, ClassName frameworkClassName, XType valueType) {
    return mapOfFrameworkType(toJavac(keyType), frameworkClassName, toJavac(valueType));
  }

  /** Returns {@code Map<KeyType, FrameworkType<ValueType>>}. */
  private TypeMirror mapOfFrameworkType(
      TypeMirror keyType, ClassName frameworkClassName, TypeMirror valueType) {
    return mapOf(
        keyType,
        types.getDeclaredType(
            elements.getTypeElement(frameworkClassName), boxPrimitives(valueType)));
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
    return forProvidesMethod(toJavac(method), toJavac(contributingModule));
  }

  public Key forProvidesMethod(ExecutableElement method, TypeElement contributingModule) {
    return forBindingMethod(method, contributingModule, Optional.of(TypeNames.PROVIDER));
  }

  public Key forProducesMethod(XMethodElement method, XTypeElement contributingModule) {
    return forProducesMethod(toJavac(method), toJavac(contributingModule));
  }

  public Key forProducesMethod(ExecutableElement method, TypeElement contributingModule) {
    return forBindingMethod(method, contributingModule, Optional.of(TypeNames.PRODUCER));
  }

  /** Returns the key bound by a {@code Binds} method. */
  Key forBindsMethod(XMethodElement method, XTypeElement contributingModule) {
    return forBindsMethod(toJavac(method), toJavac(contributingModule));
  }

  /** Returns the key bound by a {@code Binds} method. */
  Key forBindsMethod(ExecutableElement method, TypeElement contributingModule) {
    checkArgument(isAnnotationPresent(method, TypeNames.BINDS));
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
    return forBindingMethod(toJavac(method), toJavac(contributingModule), frameworkClassName);
  }

  private Key forBindingMethod(
      ExecutableElement method,
      TypeElement contributingModule,
      Optional<ClassName> frameworkClassName) {
    checkArgument(method.getKind().equals(METHOD));
    ExecutableType methodType =
        MoreTypes.asExecutable(
            types.asMemberOf(MoreTypes.asDeclared(contributingModule.asType()), method));
    ContributionType contributionType = ContributionType.fromBindingElement(method);
    TypeMirror returnType = methodType.getReturnType();
    TypeMirror keyType =
        bindingMethodKeyType(returnType, method, contributionType, frameworkClassName);
    Key key = forMethod(method, keyType);
    return contributionType.equals(ContributionType.UNIQUE)
        ? key
        : key.toBuilder()
            .multibindingContributionIdentifier(
                new MultibindingContributionIdentifier(method, contributingModule))
            .build();
  }

  private TypeMirror bindingMethodKeyType(
      TypeMirror returnType,
      ExecutableElement method,
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
    return forMethod(toJavac(method), toJavac(keyType));
  }

  private Key forMethod(ExecutableElement method, TypeMirror keyType) {
    return forQualifiedType(injectionAnnotations.getQualifier(method), keyType);
  }

  public Key forInjectConstructorWithResolvedType(XType type) {
    return forInjectConstructorWithResolvedType(toJavac(type));
  }

  public Key forInjectConstructorWithResolvedType(TypeMirror type) {
    return Key.builder(fromJava(type)).build();
  }

  // TODO(ronshapiro): Remove these conveniences which are simple wrappers around Key.Builder
  Key forType(XType type) {
    return Key.builder(DaggerType.from(type)).build();
  }

  public Key forMembersInjectedType(TypeMirror type) {
    return forMembersInjectedType(toXProcessing(type, processingEnv));
  }

  public Key forMembersInjectedType(XType type) {
    return Key.builder(DaggerType.from(type)).build();
  }

  Key forQualifiedType(Optional<AnnotationMirror> qualifier, TypeMirror type) {
    return forQualifiedType(
        qualifier.map(annotation -> toXProcessing(annotation, processingEnv)),
        toXProcessing(type, processingEnv));
  }

  Key forQualifiedType(Optional<XAnnotation> qualifier, XType type) {
    return Key.builder(DaggerType.from(type.boxed()))
        .qualifier(qualifier.map(DaggerAnnotation::from))
        .build();
  }

  private DaggerAnnotation fromJava(AnnotationMirror annotation) {
    return DaggerAnnotation.from(toXProcessing(annotation, processingEnv));
  }

  private DaggerType fromJava(TypeMirror typeMirror) {
    return DaggerType.from(toXProcessing(typeMirror, processingEnv));
  }
}
