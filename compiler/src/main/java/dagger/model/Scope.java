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

import static com.google.auto.common.MoreElements.isAnnotationPresent;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.Equivalence;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import dagger.internal.codegen.base.Preconditions;
import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.Objects;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

/** A representation of a {@link jakarta.inject.Scope}. */
// TODO(ronshapiro): point to SimpleAnnotationMirror
public final class Scope {

  private final Equivalence.Wrapper<AnnotationMirror> wrappedScopeAnnotation;

  private Scope(Equivalence.Wrapper<AnnotationMirror> wrappedScopeAnnotation) {
    this.wrappedScopeAnnotation = Objects.requireNonNull(wrappedScopeAnnotation);
  }

  /** The {@link AnnotationMirror} that represents the scope annotation. */
  public AnnotationMirror scopeAnnotation() {
    return wrappedScopeAnnotation.get();
  }

  /** The scope annotation element. */
  public TypeElement scopeAnnotationElement() {
    return MoreTypes.asTypeElement(scopeAnnotation().getAnnotationType());
  }

  /**
   * Creates a {@link Scope} object from the {@link jakarta.inject.Scope}-annotated annotation type.
   */
  public static Scope scope(AnnotationMirror scopeAnnotation) {
    Preconditions.checkArgument(isScope(scopeAnnotation));
    return new Scope(AnnotationMirrors.equivalence().wrap(scopeAnnotation));
  }

  /**
   * Returns {@code true} if {@link #scopeAnnotation()} is a {@link jakarta.inject.Scope} annotation.
   */
  public static boolean isScope(AnnotationMirror scopeAnnotation) {
    return isScope(MoreElements.asType(scopeAnnotation.getAnnotationType().asElement()));
  }

  /**
   * Returns {@code true} if {@code scopeAnnotationType} is a {@link jakarta.inject.Scope} annotation.
   */
  public static boolean isScope(TypeElement scopeAnnotationType) {
    return isAnnotationPresent(scopeAnnotationType, jakarta.inject.Scope.class);
  }

  /** Returns {@code true} if this scope is the {@link Singleton @Singleton} scope. */
  public boolean isSingleton() {
    return isScope(Singleton.class);
  }

  /** Returns {@code true} if this scope is the {@code @Reusable} scope. */
  public boolean isReusable() {
    return isScope(dagger.Reusable.class);
  }

  private boolean isScope(Class<? extends Annotation> annotation) {
    return scopeAnnotationElement().getQualifiedName().contentEquals(annotation.getCanonicalName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Scope scope = (Scope) o;
    return wrappedScopeAnnotation.equals(scope.wrappedScopeAnnotation);
  }

  @Override
  public int hashCode() {
    return wrappedScopeAnnotation.hashCode();
  }

  /** Returns a debug representation of the scope. */
  @Override
  public String toString() {
    return scopeAnnotation().toString();
  }
}
