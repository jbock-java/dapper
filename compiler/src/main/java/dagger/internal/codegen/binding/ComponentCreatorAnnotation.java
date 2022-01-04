/*
 * Copyright (C) 2019 The Dagger Authors.
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
import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;
import static java.util.stream.Collectors.mapping;

import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.base.ComponentAnnotation;
import dagger.internal.codegen.javapoet.TypeNames;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;

/** Simple representation of a component creator annotation type. */
public enum ComponentCreatorAnnotation {
  COMPONENT_BUILDER(TypeNames.COMPONENT_BUILDER),
  COMPONENT_FACTORY(TypeNames.COMPONENT_FACTORY),
  SUBCOMPONENT_BUILDER(TypeNames.SUBCOMPONENT_BUILDER),
  SUBCOMPONENT_FACTORY(TypeNames.SUBCOMPONENT_FACTORY),
  ;

  private final ClassName annotation;
  private final ComponentCreatorKind creatorKind;
  private final ClassName componentAnnotation;

  ComponentCreatorAnnotation(ClassName annotation) {
    this.annotation = annotation;
    this.creatorKind = ComponentCreatorKind.valueOf(annotation.simpleName().toUpperCase(Locale.ROOT));
    this.componentAnnotation = annotation.enclosingClassName();
  }

  /** The actual annotation type. */
  public ClassName annotation() {
    return annotation;
  }

  /** The component annotation type that encloses this creator annotation type. */
  public final ClassName componentAnnotation() {
    return componentAnnotation;
  }

  /** Returns {@code true} if the creator annotation is for a subcomponent. */
  public final boolean isSubcomponentCreatorAnnotation() {
    return componentAnnotation().simpleName().endsWith("Subcomponent");
  }

  /** The creator kind the annotation is associated with. */
  // TODO(dpb): Remove ComponentCreatorKind.
  public ComponentCreatorKind creatorKind() {
    return creatorKind;
  }

  @Override
  public final String toString() {
    return annotation().canonicalName();
  }

  /** Returns all component creator annotations. */
  public static Set<ClassName> allCreatorAnnotations() {
    return stream().collect(toAnnotationClasses());
  }

  /** Returns all subcomponent creator annotations. */
  public static Set<ClassName> subcomponentCreatorAnnotations() {
    return stream()
        .filter(
            ComponentCreatorAnnotation::isSubcomponentCreatorAnnotation)
        .collect(toAnnotationClasses());
  }

  /** Returns the legal creator annotations for the given {@code componentAnnotation}. */
  public static Set<ClassName> creatorAnnotationsFor(
      ComponentAnnotation componentAnnotation) {
    return stream()
        .filter(
            creatorAnnotation ->
                creatorAnnotation
                    .componentAnnotation()
                    .simpleName()
                    .equals(componentAnnotation.simpleName()))
        .collect(toAnnotationClasses());
  }

  /** Returns all creator annotations present on the given {@code type}. */
  public static Set<ComponentCreatorAnnotation> getCreatorAnnotations(TypeElement type) {
    return stream()
        .filter(cca -> isAnnotationPresent(type, cca.annotation()))
        .collect(toImmutableSet());
  }

  private static Stream<ComponentCreatorAnnotation> stream() {
    return Arrays.stream(ComponentCreatorAnnotation.values());
  }

  private static Collector<ComponentCreatorAnnotation, ?, Set<ClassName>>
  toAnnotationClasses() {
    return mapping(ComponentCreatorAnnotation::annotation, Collectors.toCollection(LinkedHashSet::new));
  }
}
