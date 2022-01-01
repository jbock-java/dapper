/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen.base;

import static dagger.internal.codegen.base.DiagnosticFormatting.stripCommonTypePrefixes;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.common.AnnotationMirrors;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.model.Scope;
import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;

/** Common names and convenience methods for {@link Scope}s. */
public final class Scopes {

  /** Returns a representation for {@link Singleton @Singleton} scope. */
  public static Scope singletonScope(DaggerElements elements) {
    return scope(elements, Singleton.class);
  }

  /**
   * Creates a {@link Scope} object from the {@link jakarta.inject.Scope}-annotated annotation type.
   */
  private static Scope scope(
      DaggerElements elements, Class<? extends Annotation> scopeAnnotationClass) {
    return Scope.scope(SimpleAnnotationMirror.of(elements.getTypeElement(scopeAnnotationClass)));
  }

  /**
   * Returns at most one associated scoped annotation from the source code element, throwing an
   * exception if there are more than one.
   */
  public static Optional<Scope> uniqueScopeOf(Element element) {
    Set<Scope> scopes = scopesOf(element);
    if (scopes.isEmpty()) {
      return Optional.empty();
    }
    if (scopes.size() >= 2) {
      throw new IllegalArgumentException("Expecting at most one scope but found: " + scopes);
    }
    return Optional.of(scopes.iterator().next());
  }

  /**
   * Returns the readable source representation (name with @ prefix) of the scope's annotation type.
   *
   * <p>It's readable source because it has had common package prefixes removed, e.g.
   * {@code @jakarta.inject.Singleton} is returned as {@code @Singleton}.
   */
  public static String getReadableSource(Scope scope) {
    return stripCommonTypePrefixes(scope.toString());
  }

  /** Returns all of the associated scopes for a source code element. */
  public static Set<Scope> scopesOf(Element element) {
    return AnnotationMirrors.getAnnotatedAnnotations(element, jakarta.inject.Scope.class)
        .stream()
        .map(Scope::scope)
        .collect(toImmutableSet());
  }
}
