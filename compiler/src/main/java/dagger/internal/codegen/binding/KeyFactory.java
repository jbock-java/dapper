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

import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.xprocessing.XConverters.toXProcessing;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static io.jbock.auto.common.MoreTypes.asExecutable;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.ElementKind.METHOD;

import dagger.Binds;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.javapoet.TypeNames;
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
import io.jbock.auto.common.MoreTypes;
import jakarta.inject.Inject;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;

/** A factory for {@link Key}s. */
public final class KeyFactory {
  private final XProcessingEnv processingEnv;
  private final DaggerTypes types;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  KeyFactory(
      XProcessingEnv processingEnv,
      DaggerTypes types,
      InjectionAnnotations injectionAnnotations) {
    this.processingEnv = processingEnv;
    this.types = requireNonNull(types);
    this.injectionAnnotations = injectionAnnotations;
  }

  private TypeMirror boxPrimitives(TypeMirror type) {
    return type.getKind().isPrimitive() ? types.boxedClass((PrimitiveType) type).asType() : type;
  }

  Key forComponentMethod(XMethodElement componentMethod) {
    return forMethod(componentMethod, componentMethod.getReturnType());
  }

  Key forSubcomponentCreatorMethod(
      XMethodElement subcomponentCreatorMethod, XType declaredContainer) {
    Preconditions.checkArgument(isDeclared(declaredContainer));
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
    return forBindingMethod(method, contributingModule);
  }

  /** Returns the key bound by a {@link Binds} method. */
  Key forBindsMethod(XMethodElement method, XTypeElement contributingModule) {
    return forBindsMethod(toJavac(method), toJavac(contributingModule));
  }

  /** Returns the key bound by a {@link Binds} method. */
  Key forBindsMethod(ExecutableElement method, TypeElement contributingModule) {
    Preconditions.checkArgument(isAnnotationPresent(method, TypeNames.BINDS));
    return forBindingMethod(method, contributingModule);
  }

  private Key forBindingMethod(
      XMethodElement method,
      XTypeElement contributingModule) {
    return forBindingMethod(toJavac(method), toJavac(contributingModule));
  }

  private Key forBindingMethod(
      ExecutableElement method,
      TypeElement contributingModule) {
    Preconditions.checkArgument(method.getKind().equals(METHOD));
    ExecutableType methodType =
        MoreTypes.asExecutable(
            types.asMemberOf(MoreTypes.asDeclared(contributingModule.asType()), method));
    return forMethod(method, methodType.getReturnType());
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
  Key forType(TypeMirror type) {
    return Key.builder(fromJava(type)).build();
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
