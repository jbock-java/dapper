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
import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreTypes.asTypeElements;
import static dagger.internal.codegen.base.MoreAnnotationValues.asAnnotationValues;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.javapoet.TypeNames.PRODUCER_MODULE;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnyAnnotation;
import static dagger.internal.codegen.langmodel.DaggerTypes.isTypeOf;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.javapoet.TypeNames;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * A {@code @Component}, {@code @Subcomponent}, {@code @ProductionComponent}, or
 * {@code @ProductionSubcomponent} annotation, or a {@code @Module} or {@code @ProducerModule}
 * annotation that is being treated as a component annotation when validating full binding graphs
 * for modules.
 */
public abstract class ComponentAnnotation {
  /** The root component annotation types. */
  private static final ImmutableSet<ClassName> ROOT_COMPONENT_ANNOTATIONS =
      ImmutableSet.of(TypeNames.COMPONENT, TypeNames.PRODUCTION_COMPONENT);

  /** The subcomponent annotation types. */
  private static final ImmutableSet<ClassName> SUBCOMPONENT_ANNOTATIONS =
      ImmutableSet.of(TypeNames.SUBCOMPONENT, TypeNames.PRODUCTION_SUBCOMPONENT);

  // TODO(erichang): Move ComponentCreatorAnnotation into /base and use that here?
  /** The component/subcomponent creator annotation types. */
  private static final ImmutableSet<ClassName> CREATOR_ANNOTATIONS =
      ImmutableSet.of(
          TypeNames.COMPONENT_BUILDER,
          TypeNames.COMPONENT_FACTORY,
          TypeNames.PRODUCTION_COMPONENT_BUILDER,
          TypeNames.PRODUCTION_COMPONENT_FACTORY,
          TypeNames.SUBCOMPONENT_BUILDER,
          TypeNames.SUBCOMPONENT_FACTORY,
          TypeNames.PRODUCTION_SUBCOMPONENT_BUILDER,
          TypeNames.PRODUCTION_SUBCOMPONENT_FACTORY);

  /** All component annotation types. */
  private static final ImmutableSet<ClassName> ALL_COMPONENT_ANNOTATIONS =
      ImmutableSet.<ClassName>builder()
          .addAll(ROOT_COMPONENT_ANNOTATIONS)
          .addAll(SUBCOMPONENT_ANNOTATIONS)
          .build();

  /** All component and creator annotation types. */
  private static final ImmutableSet<ClassName> ALL_COMPONENT_AND_CREATOR_ANNOTATIONS =
      ImmutableSet.<ClassName>builder()
          .addAll(ALL_COMPONENT_ANNOTATIONS)
          .addAll(CREATOR_ANNOTATIONS)
          .build();

  /** The annotation itself. */
  public abstract AnnotationMirror annotation();

  /** The simple name of the annotation type. */
  public String simpleName() {
    return MoreAnnotationMirrors.simpleName(annotation()).toString();
  }

  /**
   * Returns {@code true} if the annotation is a {@code @Subcomponent} or
   * {@code @ProductionSubcomponent}.
   */
  public abstract boolean isSubcomponent();

  /**
   * Returns {@code true} if the annotation is a {@code @ProductionComponent},
   * {@code @ProductionSubcomponent}, or {@code @ProducerModule}.
   */
  public abstract boolean isProduction();

  /**
   * Returns {@code true} if the annotation is a real component annotation and not a module
   * annotation.
   */
  public abstract boolean isRealComponent();

  /** The values listed as {@code dependencies}. */
  public abstract List<AnnotationValue> dependencyValues();

  /** The types listed as {@code dependencies}. */
  public List<TypeMirror> dependencyTypes() {
    return dependencyValues().stream().map(MoreAnnotationValues::asType).collect(toImmutableList());
  }

  /**
   * The types listed as {@code dependencies}.
   *
   * @throws IllegalArgumentException if any of {@link #dependencyTypes()} are error types
   */
  public List<TypeElement> dependencies() {
    return new ArrayList<>(asTypeElements(dependencyTypes()));
  }

  /** The values listed as {@code modules}. */
  public abstract List<AnnotationValue> moduleValues();

  /** The types listed as {@code modules}. */
  public List<TypeMirror> moduleTypes() {
    return moduleValues().stream().map(MoreAnnotationValues::asType).collect(toImmutableList());
  }

  /**
   * The types listed as {@code modules}.
   *
   * @throws IllegalArgumentException if any of {@link #moduleTypes()} are error types
   */
  public Set<TypeElement> modules() {
    return asTypeElements(moduleTypes());
  }

  protected final List<AnnotationValue> getAnnotationValues(String parameterName) {
    return asAnnotationValues(getAnnotationValue(annotation(), parameterName));
  }

  /**
   * Returns an object representing a root component annotation, not a subcomponent annotation, if
   * one is present on {@code typeElement}.
   */
  public static Optional<ComponentAnnotation> rootComponentAnnotation(TypeElement typeElement) {
    return anyComponentAnnotation(typeElement, ROOT_COMPONENT_ANNOTATIONS);
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
  public static Optional<ComponentAnnotation> anyComponentAnnotation(TypeElement typeElement) {
    return anyComponentAnnotation(typeElement, ALL_COMPONENT_ANNOTATIONS);
  }

  private static Optional<ComponentAnnotation> anyComponentAnnotation(
      TypeElement typeElement, Collection<ClassName> annotations) {
    return getAnyAnnotation(typeElement, annotations).map(ComponentAnnotation::componentAnnotation);
  }

  /** Returns {@code true} if the argument is a component annotation. */
  public static boolean isComponentAnnotation(AnnotationMirror annotation) {
    return ALL_COMPONENT_ANNOTATIONS.stream()
        .anyMatch(annotationClass -> isTypeOf(annotationClass, annotation.getAnnotationType()));
  }

  /** Creates an object representing a component or subcomponent annotation. */
  public static ComponentAnnotation componentAnnotation(AnnotationMirror annotation) {
    RealComponentAnnotation.Builder annotationBuilder =
        RealComponentAnnotation.builder().annotation(annotation);

    if (isTypeOf(TypeNames.COMPONENT, annotation.getAnnotationType())) {
      return annotationBuilder.isProduction(false).isSubcomponent(false).build();
    }
    if (isTypeOf(TypeNames.SUBCOMPONENT, annotation.getAnnotationType())) {
      return annotationBuilder.isProduction(false).isSubcomponent(true).build();
    }
    if (isTypeOf(TypeNames.PRODUCTION_COMPONENT, annotation.getAnnotationType())) {
      return annotationBuilder.isProduction(true).isSubcomponent(false).build();
    }
    if (isTypeOf(TypeNames.PRODUCTION_SUBCOMPONENT, annotation.getAnnotationType())) {
      return annotationBuilder.isProduction(true).isSubcomponent(true).build();
    }
    throw new IllegalArgumentException(
        annotation
            + " must be a Component, Subcomponent, ProductionComponent, "
            + "or ProductionSubcomponent annotation");
  }

  /** Creates a fictional component annotation representing a module. */
  public static ComponentAnnotation fromModuleAnnotation(ModuleAnnotation moduleAnnotation) {
    return new FictionalComponentAnnotation(moduleAnnotation);
  }

  /** The root component annotation types. */
  public static ImmutableSet<ClassName> rootComponentAnnotations() {
    return ROOT_COMPONENT_ANNOTATIONS;
  }

  /** The subcomponent annotation types. */
  public static ImmutableSet<ClassName> subcomponentAnnotations() {
    return SUBCOMPONENT_ANNOTATIONS;
  }

  /** All component annotation types. */
  public static ImmutableSet<ClassName> allComponentAnnotations() {
    return ALL_COMPONENT_ANNOTATIONS;
  }

  /** All component and creator annotation types. */
  public static ImmutableSet<ClassName> allComponentAndCreatorAnnotations() {
    return ALL_COMPONENT_AND_CREATOR_ANNOTATIONS;
  }

  /**
   * An actual component annotation.
   *
   * @see FictionalComponentAnnotation
   */
  static final class RealComponentAnnotation extends ComponentAnnotation {

    private final Supplier<List<AnnotationValue>> dependencyValues = Suppliers.memoize(() ->
        isSubcomponent() ? ImmutableList.of() : getAnnotationValues("dependencies"));
    private final Supplier<List<TypeMirror>> dependencyTypes = Suppliers.memoize(super::dependencyTypes);
    private final Supplier<List<TypeElement>> dependencies = Suppliers.memoize(super::dependencies);
    private final Supplier<List<AnnotationValue>> moduleValues = Suppliers.memoize(() -> getAnnotationValues("modules"));
    private final Supplier<List<TypeMirror>> moduleTypes = Suppliers.memoize(super::moduleTypes);
    private final Supplier<Set<TypeElement>> modules = Suppliers.memoize(super::modules);

    private final AnnotationMirror annotation;
    private final boolean isSubcomponent;
    private final boolean isProduction;

    private RealComponentAnnotation(
        AnnotationMirror annotation,
        boolean isSubcomponent,
        boolean isProduction) {
      this.annotation = requireNonNull(annotation);
      this.isSubcomponent = isSubcomponent;
      this.isProduction = isProduction;
    }

    @Override
    public AnnotationMirror annotation() {
      return annotation;
    }

    @Override
    public boolean isSubcomponent() {
      return isSubcomponent;
    }

    @Override
    public boolean isProduction() {
      return isProduction;
    }

    @Override
    public List<AnnotationValue> dependencyValues() {
      return dependencyValues.get();
    }

    @Override
    public List<TypeMirror> dependencyTypes() {
      return dependencyTypes.get();
    }

    @Override
    public List<TypeElement> dependencies() {
      return dependencies.get();
    }

    @Override
    public boolean isRealComponent() {
      return true;
    }

    @Override
    public List<AnnotationValue> moduleValues() {
      return moduleValues.get();
    }

    @Override
    public List<TypeMirror> moduleTypes() {
      return moduleTypes.get();
    }

    @Override
    public Set<TypeElement> modules() {
      return modules.get();
    }

    private static Builder builder() {
      return new Builder();
    }

    private static final class Builder {
      AnnotationMirror annotation;
      Boolean isSubcomponent;
      Boolean isProduction;

      ComponentAnnotation.RealComponentAnnotation.Builder annotation(AnnotationMirror annotation) {
        this.annotation = annotation;
        return this;
      }

      ComponentAnnotation.RealComponentAnnotation.Builder isSubcomponent(boolean isSubcomponent) {
        this.isSubcomponent = isSubcomponent;
        return this;
      }

      ComponentAnnotation.RealComponentAnnotation.Builder isProduction(boolean isProduction) {
        this.isProduction = isProduction;
        return this;
      }

      RealComponentAnnotation build() {
        return new RealComponentAnnotation(annotation, isSubcomponent, isProduction);
      }
    }
  }

  /**
   * A fictional component annotation used to represent modules or other collections of bindings as
   * a component.
   */
  private static final class FictionalComponentAnnotation extends ComponentAnnotation {

    final Supplier<List<TypeMirror>> moduleTypes = Suppliers.memoize(super::moduleTypes);
    final Supplier<Set<TypeElement>> modules = Suppliers.memoize(super::modules);

    final ModuleAnnotation moduleAnnotation;

    FictionalComponentAnnotation(ModuleAnnotation moduleAnnotation) {
      this.moduleAnnotation = requireNonNull(moduleAnnotation);
    }

    @Override
    public AnnotationMirror annotation() {
      return moduleAnnotation().annotation();
    }

    @Override
    public boolean isSubcomponent() {
      return false;
    }

    @Override
    public boolean isProduction() {
      return ClassName.get(asType(moduleAnnotation().annotation().getAnnotationType().asElement()))
          .equals(PRODUCER_MODULE);
    }

    @Override
    public boolean isRealComponent() {
      return false;
    }

    @Override
    public List<AnnotationValue> dependencyValues() {
      return ImmutableList.of();
    }

    @Override
    public List<AnnotationValue> moduleValues() {
      return moduleAnnotation().includesAsAnnotationValues();
    }

    @Override
    public List<TypeMirror> moduleTypes() {
      return moduleTypes.get();
    }

    @Override
    public Set<TypeElement> modules() {
      return modules.get();
    }

    private ModuleAnnotation moduleAnnotation() {
      return moduleAnnotation;
    }
  }
}
