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

import static dagger.internal.codegen.base.Suppliers.memoize;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnyAnnotation;
import static dagger.internal.codegen.xprocessing.XAnnotations.getClassName;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static io.jbock.auto.common.MoreTypes.asTypeElement;

import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XAnnotation;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.javapoet.ClassName;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;

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

  private final XAnnotation annotation;
  private final ClassName className;

  private ComponentAnnotation(
      XAnnotation annotation,
      ClassName className) {
    this.annotation = annotation;
    this.className = className;
  }

  /** The annotation itself. */
  public XAnnotation annotation() {
    return annotation;
  }

  /** Returns the {@link ClassName} name of the annotation. */
  public ClassName className() {
    return className;
  }

  /** The simple name of the annotation type. */
  public String simpleName() {
    return className().simpleName();
  }

  /**
   * Returns {@code true} if the annotation is a {@code @Subcomponent} or
   * {@code @ProductionSubcomponent}.
   */
  public boolean isSubcomponent() {
    return SUBCOMPONENT_ANNOTATIONS.contains(className());
  }

  /**
   * Returns {@code true} if the annotation is a real component annotation and not a module
   * annotation.
   */
  public boolean isRealComponent() {
    return ALL_COMPONENT_ANNOTATIONS.contains(className());
  }

  /** The types listed as {@code dependencies}. */
  private final Supplier<List<XType>> dependencyTypesCache = memoize(() -> isRootComponent()
      ? annotation().getAsTypeList("dependencies")
      : List.of());

  public List<XType> dependencyTypes() {
    return dependencyTypesCache.get();
  }

  /**
   * The types listed as {@code dependencies}.
   */
  private final Supplier<Set<XTypeElement>> dependenciesCache = memoize(() ->
      dependencyTypes().stream().map(XType::getTypeElement).collect(toImmutableSet()));

  public Set<XTypeElement> dependencies() {
    return dependenciesCache.get();
  }

  /**
   * The types listed as {@code modules}.
   */
  private final Supplier<Set<XTypeElement>> modulesCache = memoize(() ->
      annotation().getAsTypeList(isRealComponent() ? "modules" : "includes").stream()
          .map(XType::getTypeElement)
          .collect(toImmutableSet()));

  public Set<XTypeElement> modules() {
    return modulesCache.get();
  }

  private boolean isRootComponent() {
    return ROOT_COMPONENT_ANNOTATIONS.contains(className());
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
    return anyComponentAnnotation(typeElement, SUBCOMPONENT_ANNOTATIONS);
  }

  /**
   * Returns an object representing a root component or subcomponent annotation, if one is present
   * on {@code typeElement}.
   */
  public static Optional<ComponentAnnotation> anyComponentAnnotation(XElement element) {
    return anyComponentAnnotation(element, ALL_COMPONENT_ANNOTATIONS);
  }

  private static Optional<ComponentAnnotation> anyComponentAnnotation(
      XElement element, Collection<ClassName> annotations) {
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
    Preconditions.checkState(
        isComponentAnnotation(annotation),
        annotation
            + " must be a Component, Subcomponent, ProductionComponent, "
            + "or ProductionSubcomponent annotation");
    return create(annotation);
  }

  /** Creates a fictional component annotation representing a module. */
  public static ComponentAnnotation fromModuleAnnotation(ModuleAnnotation moduleAnnotation) {
    return create(moduleAnnotation.annotation());
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
    return className.equals(that.className);
  }

  @Override
  public int hashCode() {
    return className.hashCode();
  }

  private static ComponentAnnotation create(XAnnotation annotation) {
    return new ComponentAnnotation(annotation, getClassName(annotation));
  }
}
