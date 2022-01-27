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

package dagger.model;

import static io.jbock.auto.common.MoreElements.isAnnotationPresent;
import static java.util.Objects.requireNonNull;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.DaggerTypeElement;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Singleton;

/** A representation of a {@link jakarta.inject.Scope}. */
// TODO(ronshapiro): point to SimpleAnnotationMirror
public final class Scope {

  private final DaggerAnnotation scopeAnnotation;

  private Scope(DaggerAnnotation scopeAnnotation) {
    this.scopeAnnotation = requireNonNull(scopeAnnotation);
  }

  /**
   * Creates a {@link Scope} object from the {@link jakarta.inject.Scope}-annotated annotation type.
   */
  public static Scope scope(DaggerAnnotation scopeAnnotation) {
    Preconditions.checkArgument(isScope(scopeAnnotation));
    return new Scope(scopeAnnotation);
  }

  /**
   * Returns {@code true} if {@link #scopeAnnotation()} is a {@link jakarta.inject.Scope} annotation.
   */
  public static boolean isScope(DaggerAnnotation scopeAnnotation) {
    return isScope(scopeAnnotation.annotationTypeElement());
  }

  /**
   * Returns {@code true} if {@code scopeAnnotationType} is a {@link jakarta.inject.Scope} annotation.
   */
  public static boolean isScope(DaggerTypeElement scopeAnnotationType) {
    // TODO(bcorso): Replace Scope class reference with class name once auto-common is updated.
    return isAnnotationPresent(scopeAnnotationType.java(), jakarta.inject.Scope.class);
  }

  /** The {@link DaggerAnnotation} that represents the scope annotation. */
  public DaggerAnnotation scopeAnnotation() {
    return scopeAnnotation;
  }

  public ClassName className() {
    return scopeAnnotation().className();
  }

  /** Returns {@code true} if this scope is the {@link Singleton @Singleton} scope. */
  public boolean isSingleton() {
    return isScope(TypeNames.SINGLETON);
  }

  /** Returns {@code true} if this scope is the {@code @Reusable} scope. */
  public boolean isReusable() {
    return isScope(TypeNames.REUSABLE);
  }

  private boolean isScope(ClassName annotation) {
    return scopeAnnotation().className().equals(annotation);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Scope scope = (Scope) o;
    return scopeAnnotation.equals(scope.scopeAnnotation);
  }

  @Override
  public int hashCode() {
    return scopeAnnotation.hashCode();
  }

  /** Returns a debug representation of the scope. */
  @Override
  public String toString() {
    return scopeAnnotation().toString();
  }
}
