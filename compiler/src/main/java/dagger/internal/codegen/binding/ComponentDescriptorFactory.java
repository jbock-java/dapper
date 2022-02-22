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

import static dagger.internal.codegen.base.ComponentAnnotation.rootComponentAnnotation;
import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotation;
import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.base.Scopes.scopesOf;
import static dagger.internal.codegen.base.Util.getOnlyElement;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.creatorAnnotationsFor;
import static dagger.internal.codegen.binding.ComponentDescriptor.isComponentContributionMethod;
import static dagger.internal.codegen.binding.ConfigurationAnnotations.enclosedAnnotatedTypes;
import static dagger.internal.codegen.binding.ConfigurationAnnotations.isSubcomponentCreator;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.xprocessing.XConverters.toXProcessing;
import static dagger.internal.codegen.xprocessing.XTypeElements.getAllUnimplementedMethods;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static javax.lang.model.util.ElementFilter.methodsIn;

import dagger.internal.codegen.base.ComponentAnnotation;
import dagger.internal.codegen.base.ModuleAnnotation;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.collect.ImmutableBiMap;
import dagger.internal.codegen.collect.ImmutableMap;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XMethodType;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.spi.model.Scope;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;

/** A factory for {@link ComponentDescriptor}s. */
public final class ComponentDescriptorFactory {
  private final XProcessingEnv processingEnv;
  private final DaggerElements elements;
  private final DaggerTypes types;
  private final DependencyRequestFactory dependencyRequestFactory;
  private final ModuleDescriptor.Factory moduleDescriptorFactory;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  ComponentDescriptorFactory(
      XProcessingEnv processingEnv,
      DaggerElements elements,
      DaggerTypes types,
      DependencyRequestFactory dependencyRequestFactory,
      ModuleDescriptor.Factory moduleDescriptorFactory,
      InjectionAnnotations injectionAnnotations) {
    this.processingEnv = processingEnv;
    this.elements = elements;
    this.types = types;
    this.dependencyRequestFactory = dependencyRequestFactory;
    this.moduleDescriptorFactory = moduleDescriptorFactory;
    this.injectionAnnotations = injectionAnnotations;
  }

  /** Returns a descriptor for a root component type. */
  public ComponentDescriptor rootComponentDescriptor(XTypeElement typeElement) {
    Optional<ComponentAnnotation> annotation = rootComponentAnnotation(typeElement);
    Preconditions.checkArgument(annotation.isPresent(), "%s must have a component annotation", typeElement);
    return create(typeElement, annotation.get());
  }

  /** Returns a descriptor for a subcomponent type. */
  public ComponentDescriptor subcomponentDescriptor(XTypeElement typeElement) {
    Optional<ComponentAnnotation> annotation = subcomponentAnnotation(typeElement);
    Preconditions.checkArgument(annotation.isPresent(), "%s must have a subcomponent annotation", typeElement);
    return create(typeElement, annotation.get());
  }

  /**
   * Returns a descriptor for a fictional component based on a module type in order to validate its
   * bindings.
   */
  public ComponentDescriptor moduleComponentDescriptor(XTypeElement typeElement) {
    Optional<ModuleAnnotation> annotation = moduleAnnotation(typeElement);
    Preconditions.checkArgument(annotation.isPresent(), "%s must have a module annotation", typeElement);
    return create(typeElement, ComponentAnnotation.fromModuleAnnotation(annotation.get()));
  }

  private ComponentDescriptor create(
      XTypeElement typeElement, ComponentAnnotation componentAnnotation) {
    Set<ComponentRequirement> componentDependencies =
        componentAnnotation.dependencyTypes().stream()
            .map(ComponentRequirement::forDependency)
            .collect(toImmutableSet());

    Map<XMethodElement, ComponentRequirement> dependenciesByDependencyMethod =
        new LinkedHashMap<>();

    for (ComponentRequirement componentDependency : componentDependencies) {
      for (ExecutableElement dependencyMethod :
          methodsIn(elements.getAllMembers(toJavac(componentDependency.typeElement())))) {
        if (isComponentContributionMethod(dependencyMethod)) {
          dependenciesByDependencyMethod.put(
              (XMethodElement) toXProcessing(dependencyMethod, processingEnv), componentDependency);
        }
      }
    }

    // Start with the component's modules. For fictional components built from a module, start with
    // that module.
    Set<XTypeElement> modules =
        componentAnnotation.isRealComponent()
            ? componentAnnotation.modules()
            : Set.of(typeElement);

    Set<ModuleDescriptor> transitiveModules =
        moduleDescriptorFactory.transitiveModules(modules);

    Set<ComponentDescriptor> subcomponentsFromModules =
        transitiveModules.stream()
            .flatMap(transitiveModule -> transitiveModule.subcomponentDeclarations().stream())
            .map(SubcomponentDeclaration::subcomponentType)
            .map(this::subcomponentDescriptor)
            .collect(toImmutableSet());

    Set<ComponentMethodDescriptor> componentMethodsBuilder =
        new LinkedHashSet<>();
    Map<ComponentMethodDescriptor, ComponentDescriptor>
        subcomponentsByFactoryMethod = new LinkedHashMap<>();
    Map<ComponentMethodDescriptor, ComponentDescriptor>
        subcomponentsByBuilderMethod = new LinkedHashMap<>();
    if (componentAnnotation.isRealComponent()) {
      for (XMethodElement componentMethod : getAllUnimplementedMethods(typeElement)) {
        ComponentMethodDescriptor componentMethodDescriptor =
            getDescriptorForComponentMethod(typeElement, componentMethod);
        componentMethodsBuilder.add(componentMethodDescriptor);
        componentMethodDescriptor
            .subcomponent()
            .ifPresent(
                subcomponent -> {
                  // If the dependency request is present, that means the method returns the
                  // subcomponent factory.
                  if (componentMethodDescriptor.dependencyRequest().isPresent()) {
                    subcomponentsByBuilderMethod.put(componentMethodDescriptor, subcomponent);
                  } else {
                    subcomponentsByFactoryMethod.put(componentMethodDescriptor, subcomponent);
                  }
                });
      }
    }

    // Validation should have ensured that this set will have at most one element.
    Set<XTypeElement> enclosedCreators =
        enclosedAnnotatedTypes(typeElement, creatorAnnotationsFor(componentAnnotation));
    Optional<ComponentCreatorDescriptor> creatorDescriptor =
        enclosedCreators.isEmpty()
            ? Optional.empty()
            : Optional.of(
            ComponentCreatorDescriptor.create(
                getOnlyElement(enclosedCreators), types, dependencyRequestFactory));

    Set<Scope> scopes = scopesOf(typeElement);

    return ComponentDescriptor.create(
        componentAnnotation,
        toXProcessing(typeElement.toJavac(), processingEnv),
        ImmutableSet.copyOf(componentDependencies),
        ImmutableSet.copyOf(transitiveModules),
        ImmutableMap.copyOf(dependenciesByDependencyMethod),
        ImmutableSet.copyOf(scopes),
        ImmutableSet.copyOf(subcomponentsFromModules),
        ImmutableBiMap.copyOf(subcomponentsByFactoryMethod),
        ImmutableBiMap.copyOf(subcomponentsByBuilderMethod),
        ImmutableSet.copyOf(componentMethodsBuilder),
        creatorDescriptor);
  }

  private ComponentMethodDescriptor getDescriptorForComponentMethod(
      XTypeElement componentElement,
      XMethodElement componentMethod) {
    ComponentMethodDescriptor.Builder descriptor =
        ComponentMethodDescriptor.builder(componentMethod);

    XMethodType resolvedComponentMethod = componentMethod.asMemberOf(componentElement.getType());
    XType returnType = resolvedComponentMethod.getReturnType();
    if (isDeclared(returnType) && injectionAnnotations.getQualifier(componentMethod).isEmpty()) {
      XTypeElement returnTypeElement = returnType.getTypeElement();
      if (subcomponentAnnotation(returnTypeElement).isPresent()) {
        // It's a subcomponent factory method. There is no dependency request, and there could be
        // any number of parameters. Just return the descriptor.
        return descriptor.subcomponent(subcomponentDescriptor(returnTypeElement)).build();
      }
      if (isSubcomponentCreator(returnTypeElement)) {
        descriptor.subcomponent(
            subcomponentDescriptor(returnTypeElement.getEnclosingTypeElement()));
      }
    }

    if (componentMethod.getParameters().size() != 0) {
      throw new IllegalArgumentException(
          "component method has too many parameters: " + componentMethod);
    }
    Preconditions.checkArgument(
        !returnType.isVoid(),
        "component method cannot be void: %s",
        componentMethod);
    return descriptor.dependencyRequest(
            dependencyRequestFactory.forComponentProvisionMethod(
                componentMethod, resolvedComponentMethod))
        .build();
  }
}
