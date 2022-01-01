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

package dagger.internal.codegen.base;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static dagger.internal.codegen.base.MoreAnnotationValues.asAnnotationValues;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnyAnnotation;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.javapoet.TypeNames;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;

/** A {@code @Module} or {@code @ProducerModule} annotation. */
public final class ModuleAnnotation {
  private static final Set<ClassName> MODULE_ANNOTATIONS =
      new LinkedHashSet<>(List.of(TypeNames.MODULE, TypeNames.PRODUCER_MODULE));

  private final Supplier<List<TypeElement>> includes = Suppliers.memoize(() ->
      includesAsAnnotationValues().stream()
          .map(MoreAnnotationValues::asType)
          .map(MoreTypes::asTypeElement)
          .collect(toImmutableList()));

  private final Supplier<List<AnnotationValue>> includesAsAnnotationValues = Suppliers.memoize(() ->
      asAnnotationValues(getAnnotationValue(annotation(), "includes")));

  private final Supplier<List<TypeElement>> subcomponents = Suppliers.memoize(() ->
      subcomponentsAsAnnotationValues().stream()
          .map(MoreAnnotationValues::asType)
          .map(MoreTypes::asTypeElement)
          .collect(toImmutableList()));

  private final Supplier<List<AnnotationValue>> subcomponentsAsAnnotationValues = Suppliers.memoize(() ->
      asAnnotationValues(getAnnotationValue(annotation(), "subcomponents")));

  public ModuleAnnotation(AnnotationMirror annotation) {
    this.annotation = annotation;
  }

  /** The annotation itself. */
  // This does not use AnnotationMirrors.equivalence() because we want the actual annotation
  // instance.
  public AnnotationMirror annotation() {
    return annotation;
  }

  private final AnnotationMirror annotation;

  /** The simple name of the annotation. */
  public String annotationName() {
    return annotation.getAnnotationType().asElement().getSimpleName().toString();
  }

  /**
   * The types specified in the {@code includes} attribute.
   *
   * @throws IllegalArgumentException if any of the values are error types
   */
  public List<TypeElement> includes() {
    return includes.get();
  }

  /** The values specified in the {@code includes} attribute. */
  public List<AnnotationValue> includesAsAnnotationValues() {
    return includesAsAnnotationValues.get();
  }

  /**
   * The types specified in the {@code subcomponents} attribute.
   *
   * @throws IllegalArgumentException if any of the values are error types
   */
  public List<TypeElement> subcomponents() {
    return subcomponents.get();
  }

  /** The values specified in the {@code subcomponents} attribute. */
  public List<AnnotationValue> subcomponentsAsAnnotationValues() {
    return subcomponentsAsAnnotationValues.get();
  }

  /** Returns {@code true} if the argument is a {@code @Module} or {@code @ProducerModule}. */
  public static boolean isModuleAnnotation(AnnotationMirror annotation) {
    return MODULE_ANNOTATIONS.stream()
        .map(ClassName::canonicalName)
        .anyMatch(asTypeElement(annotation.getAnnotationType()).getQualifiedName()::contentEquals);
  }

  /**
   * Creates an object that represents a {@code @Module} or {@code @ProducerModule}.
   *
   * @throws IllegalArgumentException if {@link #isModuleAnnotation(AnnotationMirror)} returns
   *     {@code false}
   */
  public static ModuleAnnotation moduleAnnotation(AnnotationMirror annotation) {
    Preconditions.checkArgument(
        isModuleAnnotation(annotation),
        "%s is not a Module or ProducerModule annotation",
        annotation);
    return new ModuleAnnotation(annotation);
  }

  /**
   * Returns an object representing the {@code @Module} or {@code @ProducerModule} annotation if one
   * annotates {@code typeElement}.
   */
  public static Optional<ModuleAnnotation> moduleAnnotation(TypeElement typeElement) {
    return getAnyAnnotation(typeElement, TypeNames.MODULE, TypeNames.PRODUCER_MODULE)
        .map(ModuleAnnotation::moduleAnnotation);
  }
}
