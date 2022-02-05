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

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static io.jbock.auto.common.MoreElements.isAnnotationPresent;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import dagger.internal.codegen.xprocessing.XAnnotation;
import dagger.internal.codegen.xprocessing.XConverters;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import io.jbock.auto.common.AnnotationMirrors;
import io.jbock.auto.common.SuperficialValidation;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import java.util.Collection;
import java.util.List;
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

  public Set<XAnnotation> getQualifiers(XElement element, XProcessingEnv processingEnv) {
    return getQualifiers(element.toJavac()).stream()
        .map(qualifier -> XConverters.toXProcessing(qualifier, processingEnv))
        .collect(toImmutableSet());
  }

  public Collection<? extends AnnotationMirror> getQualifiers(Element element) {
    Set<? extends AnnotationMirror> qualifiers =
        AnnotationMirrors.getAnnotatedAnnotations(element, Qualifier.class);
    return List.copyOf(qualifiers);
  }

  /** Returns the constructors in {@code type} that are annotated with {@code Inject}. */
  public static Set<ExecutableElement> injectedConstructors(TypeElement type) {
    return constructorsIn(type.getEnclosedElements()).stream()
        .filter(constructor -> isAnnotationPresent(constructor, Inject.class))
        .collect(toImmutableSet());
  }
}
