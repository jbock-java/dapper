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
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import dagger.internal.codegen.xprocessing.XElement;
import dagger.model.Scope;
import dagger.spi.model.DaggerAnnotation;
import io.jbock.auto.common.AnnotationMirrors;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;

/** Common names and convenience methods for {@link Scope}s. */
public final class Scopes {

  /**
   * Returns at most one associated scoped annotation from the source code element, throwing an
   * exception if there are more than one.
   */
  public static Optional<Scope> uniqueScopeOf(Element element) {
    return scopesOf(element).stream().collect(toOptional());
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
  public static Set<Scope> scopesOf(XElement element) {
    return scopesOf(element.toJavac());
  }

  /** Returns all of the associated scopes for a source code element. */
  public static Set<Scope> scopesOf(Element element) {
    // TODO(bcorso): Replace Scope class reference with class name once auto-common is updated.
    return AnnotationMirrors.getAnnotatedAnnotations(element, jakarta.inject.Scope.class)
        .stream()
        .map(DaggerAnnotation::fromJava)
        .map(Scope::scope)
        .collect(toImmutableSet());
  }
}
