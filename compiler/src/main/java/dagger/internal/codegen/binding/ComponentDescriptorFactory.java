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

import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotation;
import static dagger.internal.codegen.base.Scopes.scopesOf;
import static dagger.internal.codegen.base.Util.getOnlyElement;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.creatorAnnotationsFor;
import static dagger.internal.codegen.binding.ComponentDescriptor.isComponentContributionMethod;
import static dagger.internal.codegen.binding.ConfigurationAnnotations.enclosedAnnotatedTypes;
import static dagger.internal.codegen.binding.ConfigurationAnnotations.isSubcomponentCreator;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static io.jbock.auto.common.MoreElements.asType;
import static io.jbock.auto.common.MoreTypes.asTypeElement;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.lang.model.util.ElementFilter.methodsIn;

import dagger.internal.codegen.base.ComponentAnnotation;
import dagger.internal.codegen.base.ModuleAnnotation;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Scope;
import io.jbock.auto.common.MoreTypes;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/** A factory for {@link ComponentDescriptor}s. */
public final class ComponentDescriptorFactory {
  private final DaggerElements elements;
  private final DaggerTypes types;
  private final DependencyRequestFactory dependencyRequestFactory;
  private final ModuleDescriptor.Factory moduleDescriptorFactory;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  ComponentDescriptorFactory(
      DaggerElements elements,
      DaggerTypes types,
      DependencyRequestFactory dependencyRequestFactory,
      ModuleDescriptor.Factory moduleDescriptorFactory,
      InjectionAnnotations injectionAnnotations) {
    this.elements = elements;
    this.types = types;
    this.dependencyRequestFactory = dependencyRequestFactory;
    this.moduleDescriptorFactory = moduleDescriptorFactory;
    this.injectionAnnotations = injectionAnnotations;
  }

  /** Returns a descriptor for a root component type. */
  public ComponentDescriptor rootComponentDescriptor(TypeElement typeElement) {
    return create(
        typeElement,
        checkAnnotation(
            typeElement,
            ComponentAnnotation::rootComponentAnnotation,
            "must have a component annotation"));
  }

  /** Returns a descriptor for a subcomponent type. */
  public ComponentDescriptor subcomponentDescriptor(TypeElement typeElement) {
    return create(
        typeElement,
        checkAnnotation(
            typeElement,
            ComponentAnnotation::subcomponentAnnotation,
            "must have a subcomponent annotation"));
  }

  /**
   * Returns a descriptor for a fictional component based on a module type in order to validate its
   * bindings.
   */
  public ComponentDescriptor moduleComponentDescriptor(TypeElement typeElement) {
    return create(
        typeElement,
        ComponentAnnotation.fromModuleAnnotation(
            checkAnnotation(
                typeElement, ModuleAnnotation::moduleAnnotation, "must have a module annotation")));
  }

  private static <A> A checkAnnotation(
      TypeElement typeElement,
      Function<TypeElement, Optional<A>> annotationFunction,
      String message) {
    return annotationFunction
        .apply(typeElement)
        .orElseThrow(() -> new IllegalArgumentException(typeElement + " " + message));
  }

  private ComponentDescriptor create(
      TypeElement typeElement, ComponentAnnotation componentAnnotation) {
    Set<ComponentRequirement> componentDependencies =
        componentAnnotation.dependencyTypes().stream()
            .map(ComponentRequirement::forDependency)
            .collect(toImmutableSet());

    Map<ExecutableElement, ComponentRequirement> dependenciesByDependencyMethod =
        new LinkedHashMap<>();

    for (ComponentRequirement componentDependency : componentDependencies) {
      for (ExecutableElement dependencyMethod :
          methodsIn(elements.getAllMembers(componentDependency.typeElement()))) {
        if (isComponentContributionMethod(elements, dependencyMethod)) {
          dependenciesByDependencyMethod.put(dependencyMethod, componentDependency);
        }
      }
    }

    // Start with the component's modules. For fictional components built from a module, start with
    // that module.
    Set<TypeElement> modules =
        componentAnnotation.isRealComponent()
            ? componentAnnotation.modules()
            : Set.of(typeElement);

    Set<ModuleDescriptor> transitiveModules =
        moduleDescriptorFactory.transitiveModules(modules);

    Set<ComponentDescriptor> subcomponentsFromModules = new LinkedHashSet<>();
    for (ModuleDescriptor module : transitiveModules) {
      for (SubcomponentDeclaration subcomponentDeclaration : module.subcomponentDeclarations()) {
        TypeElement subcomponent = subcomponentDeclaration.subcomponentType();
        subcomponentsFromModules.add(subcomponentDescriptor(subcomponent));
      }
    }

    Set<ComponentMethodDescriptor> componentMethodsBuilder =
        new LinkedHashSet<>();
    Map<ComponentMethodDescriptor, ComponentDescriptor>
        subcomponentsByFactoryMethod = new LinkedHashMap<>();
    Map<ComponentMethodDescriptor, ComponentDescriptor>
        subcomponentsByBuilderMethod = new LinkedHashMap<>();
    if (componentAnnotation.isRealComponent()) {
      Set<ExecutableElement> unimplementedMethods =
          elements.getUnimplementedMethods(typeElement);
      for (ExecutableElement componentMethod : unimplementedMethods) {
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
    Set<DeclaredType> enclosedCreators =
        creatorAnnotationsFor(componentAnnotation).stream()
            .flatMap(
                creatorAnnotation ->
                    enclosedAnnotatedTypes(typeElement, creatorAnnotation).stream())
            .collect(toImmutableSet());
    Optional<ComponentCreatorDescriptor> creatorDescriptor =
        enclosedCreators.isEmpty()
            ? Optional.empty()
            : Optional.of(
            ComponentCreatorDescriptor.create(
                getOnlyElement(enclosedCreators), elements, types, dependencyRequestFactory));

    Set<Scope> scopes = scopesOf(typeElement);

    return new ComponentDescriptor(
        componentAnnotation,
        typeElement,
        componentDependencies,
        transitiveModules,
        dependenciesByDependencyMethod,
        scopes,
        subcomponentsFromModules,
        subcomponentsByFactoryMethod,
        subcomponentsByBuilderMethod,
        componentMethodsBuilder,
        creatorDescriptor);
  }

  private ComponentMethodDescriptor getDescriptorForComponentMethod(
      TypeElement componentElement,
      ExecutableElement componentMethod) {
    ComponentMethodDescriptor.Builder descriptor =
        ComponentMethodDescriptor.builder(componentMethod);

    ExecutableType resolvedComponentMethod =
        MoreTypes.asExecutable(
            types.asMemberOf(MoreTypes.asDeclared(componentElement.asType()), componentMethod));
    TypeMirror returnType = resolvedComponentMethod.getReturnType();
    if (returnType.getKind().equals(DECLARED)
        && injectionAnnotations.getQualifier(componentMethod).isEmpty()) {
      TypeElement returnTypeElement = asTypeElement(returnType);
      if (subcomponentAnnotation(returnTypeElement).isPresent()) {
        // It's a subcomponent factory method. There is no dependency request, and there could be
        // any number of parameters. Just return the descriptor.
        return descriptor.subcomponent(subcomponentDescriptor(returnTypeElement)).build();
      }
      if (isSubcomponentCreator(returnTypeElement)) {
        descriptor.subcomponent(
            subcomponentDescriptor(asType(returnTypeElement.getEnclosingElement())));
      }
    }

    if (componentMethod.getParameters().size() != 0) {
      throw new IllegalArgumentException(
          "component method has too many parameters: " + componentMethod);
    }
    Preconditions.checkArgument(
        !returnType.getKind().equals(VOID),
        "component method cannot be void: %s",
        componentMethod);
    return descriptor.dependencyRequest(
            dependencyRequestFactory.forComponentProvisionMethod(
                componentMethod, resolvedComponentMethod))
        .build();
  }
}
