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

import static dagger.internal.codegen.base.MoreAnnotationValues.asAnnotationValues;
import static dagger.internal.codegen.base.Preconditions.checkState;
import static dagger.internal.codegen.base.Suppliers.memoize;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnyAnnotation;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static io.jbock.auto.common.AnnotationMirrors.getAnnotationValue;
import static io.jbock.auto.common.MoreTypes.asTypeElement;
import static io.jbock.auto.common.MoreTypes.asTypeElements;

import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XAnnotation;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.javapoet.ClassName;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * A {@code @Component}, {@code @Subcomponent}, {@code @ProductionComponent}, or
 * {@code @ProductionSubcomponent} annotation, or a {@code @Module} or {@code @ProducerModule}
 * annotation that is being treated as a component annotation when validating full binding graphs
 * for modules.
 */
public final class ComponentAnnotation {
  /** The root component annotation types. */
  private static final Set<ClassName> ROOT_COMPONENT_ANNOTATIONS =
      Set.of(TypeNames.COMPONENT);

  /** The subcomponent annotation types. */
  private static final Set<ClassName> SUBCOMPONENT_ANNOTATIONS =
      Set.of(TypeNames.SUBCOMPONENT);

  // TODO(erichang): Move ComponentCreatorAnnotation into /base and use that here?
  /** The component/subcomponent creator annotation types. */
  private static final Set<ClassName> CREATOR_ANNOTATIONS =
      new LinkedHashSet<>(List.of(
          TypeNames.COMPONENT_BUILDER,
          TypeNames.COMPONENT_FACTORY,
          TypeNames.SUBCOMPONENT_BUILDER,
          TypeNames.SUBCOMPONENT_FACTORY));

  /** All component annotation types. */
  private static final Set<ClassName> ALL_COMPONENT_ANNOTATIONS =
      new LinkedHashSet<>(Stream.of(ROOT_COMPONENT_ANNOTATIONS, SUBCOMPONENT_ANNOTATIONS)
          .flatMap(Set::stream)
          .collect(Collectors.toList()));

  /** All component and creator annotation types. */
  private static final Set<ClassName> ALL_COMPONENT_AND_CREATOR_ANNOTATIONS =
      new LinkedHashSet<>(Stream.of(ALL_COMPONENT_ANNOTATIONS, CREATOR_ANNOTATIONS)
          .flatMap(Set::stream)
          .collect(Collectors.toList()));

  private final AnnotationMirror annotation;

  public ComponentAnnotation(AnnotationMirror annotation) {
    this.annotation = annotation;
  }

  /** The annotation itself. */
  public AnnotationMirror annotation() {
    return annotation;
  }

  /** The simple name of the annotation type. */
  public String simpleName() {
    return annotationClassName().simpleName();
  }

  /**
   * Returns {@code true} if the annotation is a {@code @Subcomponent} or
   * {@code @ProductionSubcomponent}.
   */
  public boolean isSubcomponent() {
    return SUBCOMPONENT_ANNOTATIONS.contains(annotationClassName());
  }

  /**
   * Returns {@code true} if the annotation is a real component annotation and not a module
   * annotation.
   */
  public boolean isRealComponent() {
    return ALL_COMPONENT_ANNOTATIONS.contains(annotationClassName());
  }

  /** The values listed as {@code dependencies}. */
  private final Supplier<List<AnnotationValue>> dependencyValuesCache = memoize(() ->
      isRootComponent() ? getAnnotationValues("dependencies") : List.of());

  public List<AnnotationValue> dependencyValues() {
    return dependencyValuesCache.get();
  }

  /** The types listed as {@code dependencies}. */
  private final Supplier<List<TypeMirror>> dependencyTypesCache = memoize(() ->
      dependencyValues().stream()
          .map(MoreAnnotationValues::asType)
          .collect(toImmutableList()));

  public List<TypeMirror> dependencyTypes() {
    return dependencyTypesCache.get();
  }

  /**
   * The types listed as {@code dependencies}.
   */
  private final Supplier<List<TypeElement>> dependenciesCache = memoize(() ->
      List.copyOf(asTypeElements(dependencyTypes())));

  public List<TypeElement> dependencies() {
    return dependenciesCache.get();
  }

  /** The values listed as {@code modules}. */
  private final Supplier<List<AnnotationValue>> moduleValuesCache = memoize(() ->
      getAnnotationValues(isRealComponent() ? "modules" : "includes"));

  public List<AnnotationValue> moduleValues() {
    return moduleValuesCache.get();
  }

  /** The types listed as {@code modules}. */
  private final Supplier<List<TypeMirror>> moduleTypesCache = memoize(() ->
      moduleValues().stream().map(MoreAnnotationValues::asType).collect(toImmutableList()));

  public List<TypeMirror> moduleTypes() {
    return moduleTypesCache.get();
  }

  /**
   * The types listed as {@code modules}.
   */
  private final Supplier<Set<TypeElement>> modulesCache = memoize(() ->
      asTypeElements(moduleTypes()));

  public Set<TypeElement> modules() {
    return modulesCache.get();
  }

  private List<AnnotationValue> getAnnotationValues(String parameterName) {
    return asAnnotationValues(getAnnotationValue(annotation(), parameterName));
  }

  private boolean isRootComponent() {
    return ROOT_COMPONENT_ANNOTATIONS.contains(annotationClassName());
  }

  private ClassName annotationClassName() {
    return ClassName.get(asTypeElement(annotation().getAnnotationType()));
  }

  /**
   * Returns an object representing a root component annotation, not a subcomponent annotation, if
   * one is present on {@code typeElement}.
   */
  public static Optional<ComponentAnnotation> rootComponentAnnotation(XTypeElement typeElement) {
    return anyComponentAnnotation(typeElement, ROOT_COMPONENT_ANNOTATIONS);
  }

  /**
   * Returns an object representing a subcomponent annotation, if one is present on {@code
   * typeElement}.
   */
  public static Optional<ComponentAnnotation> subcomponentAnnotation(XTypeElement typeElement) {
    return subcomponentAnnotation(toJavac(typeElement));
  }

  /**
   * Returns an object representing a subcomponent annotation, if one is present on {@code
   * typeElement}.
   */
  public static Optional<ComponentAnnotation> subcomponentAnnotation(TypeElement typeElement) {
    return anyComponentAnnotation(typeElement, SUBCOMPONENT_ANNOTATIONS);
  }

  /**
   * Returns an object representing a root component or subcomponent annotation, if one is present
   * on {@code typeElement}.
   */
  public static Optional<ComponentAnnotation> anyComponentAnnotation(XElement element) {
    return anyComponentAnnotation(toJavac(element), ALL_COMPONENT_ANNOTATIONS);
  }

  private static Optional<ComponentAnnotation> anyComponentAnnotation(
      XElement element, Collection<ClassName> annotations) {
    return anyComponentAnnotation(toJavac(element), annotations);
  }

  private static Optional<ComponentAnnotation> anyComponentAnnotation(
      Element element, Collection<ClassName> annotations) {
    return getAnyAnnotation(element, annotations).map(ComponentAnnotation::componentAnnotation);
  }

  /** Returns {@code true} if the argument is a component annotation. */
  public static boolean isComponentAnnotation(XAnnotation annotation) {
    return isComponentAnnotation(toJavac(annotation));
  }

  /** Returns {@code true} if the argument is a component annotation. */
  public static boolean isComponentAnnotation(AnnotationMirror annotation) {
    ClassName className = ClassName.get(asTypeElement(annotation.getAnnotationType()));
    return ALL_COMPONENT_ANNOTATIONS.contains(className);
  }

  /** Creates an object representing a component or subcomponent annotation. */
  public static ComponentAnnotation componentAnnotation(XAnnotation annotation) {
    return componentAnnotation(toJavac(annotation));
  }

  /** Creates an object representing a component or subcomponent annotation. */
  public static ComponentAnnotation componentAnnotation(AnnotationMirror annotation) {
    checkState(
        isComponentAnnotation(annotation),
        annotation
            + " must be a Component, Subcomponent, ProductionComponent, "
            + "or ProductionSubcomponent annotation");
    return new ComponentAnnotation(annotation);
  }

  /** Creates a fictional component annotation representing a module. */
  public static ComponentAnnotation fromModuleAnnotation(ModuleAnnotation moduleAnnotation) {
    return new ComponentAnnotation(moduleAnnotation.annotation());
  }

  /** The root component annotation types. */
  public static Set<ClassName> rootComponentAnnotations() {
    return ROOT_COMPONENT_ANNOTATIONS;
  }

  /** The subcomponent annotation types. */
  public static Set<ClassName> subcomponentAnnotations() {
    return SUBCOMPONENT_ANNOTATIONS;
  }

  /** All component annotation types. */
  public static Set<ClassName> allComponentAnnotations() {
    return ALL_COMPONENT_ANNOTATIONS;
  }

  /** All component and creator annotation types. */
  public static Set<ClassName> allComponentAndCreatorAnnotations() {
    return ALL_COMPONENT_AND_CREATOR_ANNOTATIONS;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ComponentAnnotation that = (ComponentAnnotation) o;
    return annotation.equals(that.annotation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(annotation);
  }
}
