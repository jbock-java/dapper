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

import static io.jbock.auto.common.MoreElements.isAnnotationPresent;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import dagger.internal.codegen.extension.DaggerStreams;
import io.jbock.auto.common.AnnotationMirrors;
import io.jbock.auto.common.SuperficialValidation;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Utilities relating to annotations defined in the {@code javax.inject} package. */
public final class InjectionAnnotations {

  @Inject
  InjectionAnnotations() {
  }

  public Optional<AnnotationMirror> getQualifier(Element e) {
    if (!SuperficialValidation.validateElement(e)) {
      throw new TypeNotPresentException(e.toString(), null);
    }
    requireNonNull(e);
    Collection<? extends AnnotationMirror> qualifierAnnotations = getQualifiers(e);
    switch (qualifierAnnotations.size()) {
      case 0:
        return Optional.empty();
      case 1:
        return Optional.of(qualifierAnnotations.iterator().next());
      default:
        throw new IllegalArgumentException(
            e + " was annotated with more than one @Qualifier annotation");
    }
  }

  public Collection<? extends AnnotationMirror> getQualifiers(Element element) {
    Set<? extends AnnotationMirror> qualifiers =
        AnnotationMirrors.getAnnotatedAnnotations(element, jakarta.inject.Qualifier.class);
    return new ArrayList<>(qualifiers);
  }

  /** Returns the constructors in {@code type} that are annotated with {@code Inject}. */
  public static Set<ExecutableElement> injectedConstructors(TypeElement type) {
    return constructorsIn(type.getEnclosedElements()).stream()
        .filter(constructor -> isAnnotationPresent(constructor, Inject.class))
        .collect(DaggerStreams.toImmutableSet());
  }
}
