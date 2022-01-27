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

import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.base.Util.getOnlyElement;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.getCreatorAnnotations;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static io.jbock.auto.common.MoreElements.isAnnotationPresent;
import static io.jbock.auto.common.MoreTypes.asTypeElement;
import static java.util.Objects.requireNonNull;

import dagger.BindsInstance;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import io.jbock.auto.common.MoreTypes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/**
 * A descriptor for a component <i>creator</i> type: that is, a type annotated with
 * {@code @Component.Builder} (or one of the corresponding production or subcomponent versions).
 */
public final class ComponentCreatorDescriptor {

  private final Supplier<Map<ComponentRequirement, Element>> requirementElements = Suppliers.memoize(() ->
      flatten(unvalidatedRequirementElements()));
  private final Supplier<Map<ComponentRequirement, ExecutableElement>> setterMethods = Suppliers.memoize(() ->
      flatten(unvalidatedSetterMethods()));
  private final Supplier<Map<ComponentRequirement, VariableElement>> factoryParameters = Suppliers.memoize(() ->
      flatten(unvalidatedFactoryParameters()));

  private final ComponentCreatorAnnotation annotation;
  private final TypeElement typeElement;
  private final ExecutableElement factoryMethod;
  private final Map<ComponentRequirement, Set<ExecutableElement>> unvalidatedSetterMethods;
  private final Map<ComponentRequirement, Set<VariableElement>> unvalidatedFactoryParameters;

  private ComponentCreatorDescriptor(
      ComponentCreatorAnnotation annotation,
      TypeElement typeElement,
      ExecutableElement factoryMethod,
      Map<ComponentRequirement, Set<ExecutableElement>> unvalidatedSetterMethods,
      Map<ComponentRequirement, Set<VariableElement>> unvalidatedFactoryParameters) {
    this.annotation = requireNonNull(annotation);
    this.typeElement = requireNonNull(typeElement);
    this.factoryMethod = requireNonNull(factoryMethod);
    this.unvalidatedSetterMethods = requireNonNull(unvalidatedSetterMethods);
    this.unvalidatedFactoryParameters = requireNonNull(unvalidatedFactoryParameters);
  }

  /** Returns the annotation marking this creator. */
  public ComponentCreatorAnnotation annotation() {
    return annotation;
  }

  /** The kind of this creator. */
  public ComponentCreatorKind kind() {
    return annotation().creatorKind();
  }

  /** The annotated creator type. */
  public TypeElement typeElement() {
    return typeElement;
  }

  /** The method that creates and returns a component instance. */
  public ExecutableElement factoryMethod() {
    return factoryMethod;
  }

  /**
   * Multimap of component requirements to setter methods that set that requirement.
   *
   * <p>In a valid creator, there will be exactly one element per component requirement, so this
   * method should only be called when validating the descriptor.
   */
  Map<ComponentRequirement, Set<ExecutableElement>> unvalidatedSetterMethods() {
    return unvalidatedSetterMethods;
  }

  /**
   * Multimap of component requirements to factory method parameters that set that requirement.
   *
   * <p>In a valid creator, there will be exactly one element per component requirement, so this
   * method should only be called when validating the descriptor.
   */
  Map<ComponentRequirement, Set<VariableElement>> unvalidatedFactoryParameters() {
    return unvalidatedFactoryParameters;
  }

  /**
   * Multimap of component requirements to elements (methods or parameters) that set that
   * requirement.
   *
   * <p>In a valid creator, there will be exactly one element per component requirement, so this
   * method should only be called when validating the descriptor.
   */
  @SuppressWarnings(value = {"unchecked", "rawtypes"})
  public Map<ComponentRequirement, Set<Element>>
  unvalidatedRequirementElements() {
    // ComponentCreatorValidator ensures that there are either setter methods or factory method
    // parameters, but not both, so we can cheat a little here since we know that only one of
    // the two multimaps will be non-empty.
    return unvalidatedSetterMethods().isEmpty()
        ? (Map) unvalidatedFactoryParameters()
        : (Map) unvalidatedSetterMethods();
  }

  /**
   * Map of component requirements to elements (setter methods or factory method parameters) that
   * set them.
   */
  Map<ComponentRequirement, Element> requirementElements() {
    return requirementElements.get();
  }

  /** Map of component requirements to setter methods for those requirements. */
  public Map<ComponentRequirement, ExecutableElement> setterMethods() {
    return setterMethods.get();
  }

  /** Map of component requirements to factory method parameters for those requirements. */
  public Map<ComponentRequirement, VariableElement> factoryParameters() {
    return factoryParameters.get();
  }

  private static <K, V> Map<K, V> flatten(Map<K, Set<V>> multimap) {
    return Util.transformValues(multimap, Util::getOnlyElement);
  }

  /** Returns the set of component requirements this creator allows the user to set. */
  public Set<ComponentRequirement> userSettableRequirements() {
    // Note: they should have been validated at the point this is used, so this set is valid.
    return unvalidatedRequirementElements().keySet();
  }

  /** Returns the set of requirements for modules and component dependencies for this creator. */
  public Set<ComponentRequirement> moduleAndDependencyRequirements() {
    return userSettableRequirements().stream()
        .filter(requirement -> !requirement.isBoundInstance())
        .collect(toImmutableSet());
  }

  /** Returns the set of bound instance requirements for this creator. */
  Set<ComponentRequirement> boundInstanceRequirements() {
    return userSettableRequirements().stream()
        .filter(ComponentRequirement::isBoundInstance)
        .collect(toImmutableSet());
  }

  /** Returns the element in this creator that sets the given {@code requirement}. */
  Element elementForRequirement(ComponentRequirement requirement) {
    return requirementElements().get(requirement);
  }

  /** Creates a new {@link ComponentCreatorDescriptor} for the given creator {@code type}. */
  public static ComponentCreatorDescriptor create(
      DeclaredType type,
      DaggerElements elements,
      DaggerTypes types,
      DependencyRequestFactory dependencyRequestFactory) {
    TypeElement typeElement = asTypeElement(type);
    TypeMirror componentType = typeElement.getEnclosingElement().asType();

    Map<ComponentRequirement, Set<ExecutableElement>> setterMethods =
        new LinkedHashMap<>();

    ExecutableElement factoryMethod = null;
    for (ExecutableElement method : elements.getUnimplementedMethods(typeElement)) {
      ExecutableType resolvedMethodType = MoreTypes.asExecutable(types.asMemberOf(type, method));

      if (types.isSubtype(componentType, resolvedMethodType.getReturnType())) {
        factoryMethod = method;
      } else {
        VariableElement parameter = getOnlyElement(method.getParameters());
        TypeMirror parameterType = getOnlyElement(resolvedMethodType.getParameterTypes());
        setterMethods.merge(
            requirement(method, parameter, parameterType, dependencyRequestFactory, method),
            Set.of(method), Util::mutableUnion);
      }
    }
    Preconditions.checkState(factoryMethod != null); // validation should have ensured this.

    Map<ComponentRequirement, Set<VariableElement>> factoryParameters =
        new LinkedHashMap<>();

    ExecutableType resolvedFactoryMethodType =
        MoreTypes.asExecutable(types.asMemberOf(type, factoryMethod));
    List<? extends VariableElement> parameters = factoryMethod.getParameters();
    List<? extends TypeMirror> parameterTypes = resolvedFactoryMethodType.getParameterTypes();
    for (int i = 0; i < parameters.size(); i++) {
      VariableElement parameter = parameters.get(i);
      TypeMirror parameterType = parameterTypes.get(i);
      factoryParameters.merge(
          requirement(factoryMethod, parameter, parameterType, dependencyRequestFactory, parameter),
          Set.of(parameter), Util::mutableUnion);
    }

    // Validation should have ensured exactly one creator annotation is present on the type.
    ComponentCreatorAnnotation annotation = getOnlyElement(getCreatorAnnotations(typeElement));
    return new ComponentCreatorDescriptor(
        annotation, typeElement, factoryMethod,
        setterMethods,
        factoryParameters);
  }

  private static ComponentRequirement requirement(
      ExecutableElement method,
      VariableElement parameter,
      TypeMirror type,
      DependencyRequestFactory dependencyRequestFactory,
      Element elementForVariableName) {
    if (isAnnotationPresent(method, BindsInstance.class)
        || isAnnotationPresent(parameter, BindsInstance.class)) {
      DependencyRequest request =
          dependencyRequestFactory.forRequiredResolvedVariable(parameter, type);
      String variableName = elementForVariableName.getSimpleName().toString();
      return ComponentRequirement.forBoundInstance(
          request.key(), variableName);
    }

    return moduleAnnotation(asTypeElement(type)).isPresent()
        ? ComponentRequirement.forModule(type)
        : ComponentRequirement.forDependency(type);
  }
}
