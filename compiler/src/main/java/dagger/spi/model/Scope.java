/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.spi.model;

import static dagger.internal.codegen.base.Preconditions.checkArgument;
import static io.jbock.auto.common.MoreElements.isAnnotationPresent;

import io.jbock.auto.value.AutoValue;
import io.jbock.javapoet.ClassName;

/** A representation of a {@code jakarta.inject.Scope}. */
@AutoValue
public abstract class Scope {
  /**
   * Creates a {@code Scope} object from the {@code jakarta.inject.Scope}-annotated annotation type.
   */
  public static Scope scope(DaggerAnnotation scopeAnnotation) {
    checkArgument(isScope(scopeAnnotation));
    return new AutoValue_Scope(scopeAnnotation);
  }

  /**
   * Returns {@code true} if {@code #scopeAnnotation()} is a {@code jakarta.inject.Scope} annotation.
   */
  public static boolean isScope(DaggerAnnotation scopeAnnotation) {
    return isScope(scopeAnnotation.annotationTypeElement());
  }

  /**
   * Returns {@code true} if {@code scopeAnnotationType} is a {@code jakarta.inject.Scope} annotation.
   */
  public static boolean isScope(DaggerTypeElement scopeAnnotationType) {
    // TODO(bcorso): Replace Scope class reference with class name once auto-common is updated.
    return isAnnotationPresent(scopeAnnotationType.java(), jakarta.inject.Scope.class);
  }

  private static final ClassName PRODUCTION_SCOPE =
      ClassName.get("dagger.producers", "ProductionScope");
  private static final ClassName SINGLETON = ClassName.get("javax.inject", "Singleton");
  private static final ClassName REUSABLE = ClassName.get("dagger", "Reusable");
  private static final ClassName SCOPE = ClassName.get("javax.inject", "Scope");


  /** The {@code DaggerAnnotation} that represents the scope annotation. */
  public abstract DaggerAnnotation scopeAnnotation();

  public final ClassName className() {
    return scopeAnnotation().className();
  }

  /** Returns {@code true} if this scope is the {@code jakarta.inject.Singleton @Singleton} scope. */
  public final boolean isSingleton() {
    return isScope(SINGLETON);
  }

  /** Returns {@code true} if this scope is the {@code dagger.Reusable @Reusable} scope. */
  public final boolean isReusable() {
    return isScope(REUSABLE);
  }

  /**
   * Returns {@code true} if this scope is the {@code
   * dagger.producers.ProductionScope @ProductionScope} scope.
   */
  public final boolean isProductionScope() {
    return isScope(PRODUCTION_SCOPE);
  }

  private boolean isScope(ClassName annotation) {
    return scopeAnnotation().className().equals(annotation);
  }

  /** Returns a debug representation of the scope. */
  @Override
  public final String toString() {
    return scopeAnnotation().toString();
  }
}
