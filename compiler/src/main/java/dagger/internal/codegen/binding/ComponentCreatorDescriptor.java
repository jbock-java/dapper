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
import static dagger.internal.codegen.xprocessing.XTypeElements.getAllUnimplementedMethods;
import static java.util.Objects.requireNonNull;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XExecutableParameterElement;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XMethodType;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.model.DependencyRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A descriptor for a component <i>creator</i> type: that is, a type annotated with
 * {@code @Component.Builder} (or one of the corresponding production or subcomponent versions).
 */
public final class ComponentCreatorDescriptor {

  private final Supplier<Map<ComponentRequirement, XElement>> requirementElements = Suppliers.memoize(() ->
      flatten(unvalidatedRequirementElements()));
  private final Supplier<Map<ComponentRequirement, XMethodElement>> setterMethods = Suppliers.memoize(() ->
      flatten(unvalidatedSetterMethods()));
  private final Supplier<Map<ComponentRequirement, XExecutableParameterElement>> factoryParameters = Suppliers.memoize(() ->
      flatten(unvalidatedFactoryParameters()));

  private final ComponentCreatorAnnotation annotation;
  private final XTypeElement typeElement;
  private final XMethodElement factoryMethod;
  private final Map<ComponentRequirement, Set<XMethodElement>> unvalidatedSetterMethods;
  private final Map<ComponentRequirement, Set<XExecutableParameterElement>> unvalidatedFactoryParameters;

  private ComponentCreatorDescriptor(
      ComponentCreatorAnnotation annotation,
      XTypeElement typeElement,
      XMethodElement factoryMethod,
      Map<ComponentRequirement, Set<XMethodElement>> unvalidatedSetterMethods,
      Map<ComponentRequirement, Set<XExecutableParameterElement>> unvalidatedFactoryParameters) {
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
  public XTypeElement typeElement() {
    return typeElement;
  }

  /** The method that creates and returns a component instance. */
  public XMethodElement factoryMethod() {
    return factoryMethod;
  }

  /**
   * Multimap of component requirements to setter methods that set that requirement.
   *
   * <p>In a valid creator, there will be exactly one element per component requirement, so this
   * method should only be called when validating the descriptor.
   */
  Map<ComponentRequirement, Set<XMethodElement>> unvalidatedSetterMethods() {
    return unvalidatedSetterMethods;
  }

  /**
   * Multimap of component requirements to factory method parameters that set that requirement.
   *
   * <p>In a valid creator, there will be exactly one element per component requirement, so this
   * method should only be called when validating the descriptor.
   */
  Map<ComponentRequirement, Set<XExecutableParameterElement>> unvalidatedFactoryParameters() {
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
  public Map<ComponentRequirement, Set<XElement>>
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
  Map<ComponentRequirement, XElement> requirementElements() {
    return requirementElements.get();
  }

  /** Map of component requirements to setter methods for those requirements. */
  public Map<ComponentRequirement, XMethodElement> setterMethods() {
    return setterMethods.get();
  }

  /** Map of component requirements to factory method parameters for those requirements. */
  public Map<ComponentRequirement, XExecutableParameterElement> factoryParameters() {
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
  XElement elementForRequirement(ComponentRequirement requirement) {
    return requirementElements().get(requirement);
  }

  /** Creates a new {@link ComponentCreatorDescriptor} for the given creator {@code type}. */
  public static ComponentCreatorDescriptor create(
      XTypeElement creator,
      DaggerTypes types,
      DependencyRequestFactory dependencyRequestFactory) {
    XType componentType = creator.getEnclosingTypeElement().getType();

    Map<ComponentRequirement, Set<XMethodElement>> setterMethods =
        new LinkedHashMap<>();

    XMethodElement factoryMethod = null;
    for (XMethodElement method : getAllUnimplementedMethods(creator)) {
      XMethodType resolvedMethodType = method.asMemberOf(creator.getType());

      if (types.isSubtype(componentType, resolvedMethodType.getReturnType())) {
        Preconditions.checkState(factoryMethod == null); // validation should have ensured there's only 1.
        factoryMethod = method;
      } else {
        XExecutableParameterElement parameter = getOnlyElement(method.getParameters());
        XType parameterType = getOnlyElement(resolvedMethodType.getParameterTypes());
        setterMethods.merge(
            requirement(
                method, parameter, parameterType, dependencyRequestFactory, method.getName()),
            Set.of(method),
            Util::mutableUnion);
      }
    }
    Preconditions.checkState(factoryMethod != null); // validation should have ensured this.

    Map<ComponentRequirement, Set<XExecutableParameterElement>> factoryParameters =
        new LinkedHashMap<>();

    XMethodType resolvedFactoryMethodType = factoryMethod.asMemberOf(creator.getType());
    List<XExecutableParameterElement> parameters = factoryMethod.getParameters();
    List<XType> parameterTypes = resolvedFactoryMethodType.getParameterTypes();
    for (int i = 0; i < parameters.size(); i++) {
      XExecutableParameterElement parameter = parameters.get(i);
      XType parameterType = parameterTypes.get(i);
      factoryParameters.merge(
          requirement(
              factoryMethod,
              parameter,
              parameterType,
              dependencyRequestFactory,
              parameter.getName()),
          Set.of(parameter),
          Util::mutableUnion);
    }
    // Validation should have ensured exactly one creator annotation is present on the type.
    ComponentCreatorAnnotation annotation = getOnlyElement(getCreatorAnnotations(creator));
    return new ComponentCreatorDescriptor(
        annotation, creator, factoryMethod, setterMethods, factoryParameters);  }

  private static ComponentRequirement requirement(
      XMethodElement method,
      XExecutableParameterElement parameter,
      XType parameterType,
      DependencyRequestFactory dependencyRequestFactory,
      String variableName) {
    if (method.hasAnnotation(TypeNames.BINDS_INSTANCE)
        || parameter.hasAnnotation(TypeNames.BINDS_INSTANCE)) {
      DependencyRequest request =
          dependencyRequestFactory.forRequiredResolvedVariable(parameter, parameterType);
      return ComponentRequirement.forBoundInstance(
          request.key(), variableName);
    }

    return moduleAnnotation(parameterType.getTypeElement()).isPresent()
        ? ComponentRequirement.forModule(parameterType)
        : ComponentRequirement.forDependency(parameterType);
  }
}
