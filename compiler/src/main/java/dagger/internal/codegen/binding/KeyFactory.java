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

import static dagger.spi.model.DaggerType.fromJava;
import static io.jbock.auto.common.MoreElements.isAnnotationPresent;
import static io.jbock.auto.common.MoreTypes.asExecutable;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.ElementKind.METHOD;

import dagger.Binds;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.spi.model.DaggerAnnotation;
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
  private final DaggerTypes types;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  KeyFactory(
      DaggerTypes types, InjectionAnnotations injectionAnnotations) {
    this.types = requireNonNull(types);
    this.injectionAnnotations = injectionAnnotations;
  }

  private TypeMirror boxPrimitives(TypeMirror type) {
    return type.getKind().isPrimitive() ? types.boxedClass((PrimitiveType) type).asType() : type;
  }

  Key forComponentMethod(ExecutableElement componentMethod) {
    Preconditions.checkArgument(componentMethod.getKind().equals(METHOD));
    return forMethod(componentMethod, componentMethod.getReturnType());
  }

  Key forSubcomponentCreatorMethod(
      ExecutableElement subcomponentCreatorMethod, DeclaredType declaredContainer) {
    Preconditions.checkArgument(subcomponentCreatorMethod.getKind().equals(METHOD));
    ExecutableType resolvedMethod =
        asExecutable(types.asMemberOf(declaredContainer, subcomponentCreatorMethod));
    return Key.builder(fromJava(resolvedMethod.getReturnType())).build();
  }

  public Key forSubcomponentCreator(TypeMirror creatorType) {
    return Key.builder(fromJava(creatorType)).build();
  }

  public Key forProvidesMethod(ExecutableElement method, TypeElement contributingModule) {
    return forBindingMethod(
        method, contributingModule);
  }

  /** Returns the key bound by a {@link Binds} method. */
  Key forBindsMethod(ExecutableElement method, TypeElement contributingModule) {
    Preconditions.checkArgument(isAnnotationPresent(method, Binds.class));
    return forBindingMethod(method, contributingModule);
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

  private Key forMethod(ExecutableElement method, TypeMirror keyType) {
    return forQualifiedType(injectionAnnotations.getQualifier(method), keyType);
  }

  public Key forInjectConstructorWithResolvedType(TypeMirror type) {
    return Key.builder(fromJava(type)).build();
  }

  // TODO(ronshapiro): Remove these conveniences which are simple wrappers around Key.Builder
  Key forType(TypeMirror type) {
    return Key.builder(fromJava(type)).build();
  }

  Key forQualifiedType(Optional<AnnotationMirror> qualifier, TypeMirror type) {
    return Key.builder(fromJava(boxPrimitives(type)))
        .qualifier(qualifier.map(DaggerAnnotation::fromJava))
        .build();
  }
}
