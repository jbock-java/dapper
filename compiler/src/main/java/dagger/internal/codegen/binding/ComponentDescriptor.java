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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import dagger.Component;
import dagger.Module;
import dagger.Subcomponent;
import dagger.internal.codegen.base.ComponentAnnotation;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import dagger.model.Scope;
import dagger.producers.CancellationPolicy;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.base.Suppliers.memoizeInt;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.type.TypeKind.VOID;

/**
 * A component declaration.
 *
 * <p>Represents one type annotated with {@code @Component}, {@code Subcomponent},
 * {@code @ProductionComponent}, or {@code @ProductionSubcomponent}.
 *
 * <p>When validating bindings installed in modules, a {@link ComponentDescriptor} can also
 * represent a synthetic component for the module, where there is an entry point for each binding in
 * the module.
 */
public final class ComponentDescriptor {

  private final ComponentAnnotation annotation;
  private final TypeElement typeElement;
  private final Set<ComponentRequirement> dependencies;
  private final Set<ModuleDescriptor> modules;
  private final Map<ExecutableElement, ComponentRequirement> dependenciesByDependencyMethod;
  private final Set<Scope> scopes;
  private final Set<ComponentDescriptor> childComponentsDeclaredByModules;
  private final ImmutableBiMap<ComponentDescriptor.ComponentMethodDescriptor, ComponentDescriptor> childComponentsDeclaredByFactoryMethods;
  private final ImmutableBiMap<ComponentDescriptor.ComponentMethodDescriptor, ComponentDescriptor> childComponentsDeclaredByBuilderEntryPoints;
  private final Set<ComponentDescriptor.ComponentMethodDescriptor> componentMethods;
  private final Optional<ComponentCreatorDescriptor> creatorDescriptor;

  ComponentDescriptor(
      ComponentAnnotation annotation,
      TypeElement typeElement,
      Set<ComponentRequirement> dependencies,
      Set<ModuleDescriptor> modules,
      Map<ExecutableElement, ComponentRequirement> dependenciesByDependencyMethod,
      Set<Scope> scopes,
      Set<ComponentDescriptor> childComponentsDeclaredByModules,
      ImmutableBiMap<ComponentDescriptor.ComponentMethodDescriptor, ComponentDescriptor> childComponentsDeclaredByFactoryMethods,
      ImmutableBiMap<ComponentDescriptor.ComponentMethodDescriptor, ComponentDescriptor> childComponentsDeclaredByBuilderEntryPoints,
      Set<ComponentDescriptor.ComponentMethodDescriptor> componentMethods,
      Optional<ComponentCreatorDescriptor> creatorDescriptor) {
    this.annotation = requireNonNull(annotation);
    this.typeElement = requireNonNull(typeElement);
    this.dependencies = requireNonNull(dependencies);
    this.modules = requireNonNull(modules);
    this.dependenciesByDependencyMethod = requireNonNull(dependenciesByDependencyMethod);
    this.scopes = requireNonNull(scopes);
    this.childComponentsDeclaredByModules = requireNonNull(childComponentsDeclaredByModules);
    this.childComponentsDeclaredByFactoryMethods = requireNonNull(childComponentsDeclaredByFactoryMethods);
    this.childComponentsDeclaredByBuilderEntryPoints = requireNonNull(childComponentsDeclaredByBuilderEntryPoints);
    this.componentMethods = requireNonNull(componentMethods);
    this.creatorDescriptor = requireNonNull(creatorDescriptor);
  }

  private final Supplier<Set<ComponentRequirement>> requirements = Suppliers.memoize(() -> {
    ImmutableSet.Builder<ComponentRequirement> requirements = ImmutableSet.builder();
    modules().stream()
        .filter(
            module ->
                module.bindings().stream().anyMatch(ContributionBinding::requiresModuleInstance))
        .map(module -> ComponentRequirement.forModule(module.moduleElement().asType()))
        .forEach(requirements::add);
    requirements.addAll(dependencies());
    requirements.addAll(
        creatorDescriptor()
            .map(ComponentCreatorDescriptor::boundInstanceRequirements)
            .orElse(ImmutableSet.of()));
    return requirements.build();
  });

  private final IntSupplier hashCode = memoizeInt(() -> {
    // TODO(b/122962745): Only use typeElement().hashCode()
    return Objects.hash(typeElement(), annotation());
  });

  private final Supplier<Map<TypeElement, ComponentDescriptor>> childComponentsByElement = Suppliers.memoize(() ->
      Maps.uniqueIndex(childComponents(), ComponentDescriptor::typeElement));

  private final Supplier<Map<BindingRequest, ComponentMethodDescriptor>> firstMatchingComponentMethods = Suppliers.memoize(() -> {
        Map<BindingRequest, ComponentMethodDescriptor> methods = new HashMap<>();
        for (ComponentMethodDescriptor method : entryPointMethods()) {
          methods.putIfAbsent(BindingRequest.bindingRequest(method.dependencyRequest().get()), method);
        }
        return ImmutableMap.copyOf(methods);
      });

  /** The annotation that specifies that {@link #typeElement()} is a component. */
  public ComponentAnnotation annotation() {
    return annotation;
  }

  /** Returns {@code true} if this is a subcomponent. */
  public boolean isSubcomponent() {
    return annotation().isSubcomponent();
  }

  /**
   * Returns {@code true} if this is a production component or subcomponent, or a
   * {@code @ProducerModule} when doing module binding validation.
   */
  public boolean isProduction() {
    return annotation().isProduction();
  }

  /**
   * Returns {@code true} if this is a real component, and not a fictional one used to validate
   * module bindings.
   */
  public boolean isRealComponent() {
    return annotation().isRealComponent();
  }

  /**
   * The element that defines the component. This is the element to which the {@link #annotation()}
   * was applied.
   */
  public TypeElement typeElement() {
    return typeElement;
  }

  /**
   * The set of component dependencies listed in {@link Component#dependencies} or {@link
   * dagger.producers.ProductionComponent#dependencies()}.
   */
  public Set<ComponentRequirement> dependencies() {
    return dependencies;
  }

  /** The non-abstract {@link #modules()} and the {@link #dependencies()}. */
  public Set<ComponentRequirement> dependenciesAndConcreteModules() {
    return Stream.concat(
            moduleTypes().stream()
                .filter(dep -> !dep.getModifiers().contains(ABSTRACT))
                .map(module -> ComponentRequirement.forModule(module.asType())),
            dependencies().stream())
        .collect(toImmutableSet());
  }

  /**
   * The {@link ModuleDescriptor modules} declared in {@link Component#modules()} and reachable by
   * traversing {@link Module#includes()}.
   */
  public Set<ModuleDescriptor> modules() {
    return modules;
  }

  /** The types of the {@link #modules()}. */
  public Set<TypeElement> moduleTypes() {
    return modules().stream().map(ModuleDescriptor::moduleElement).collect(toImmutableSet());
  }

  /**
   * The types for which the component will need instances if all of its bindings are used. For the
   * types the component will need in a given binding graph, use {@link
   * BindingGraph#componentRequirements()}.
   *
   * <ul>
   *   <li>{@linkplain #modules()} modules} with concrete instance bindings
   *   <li>Bound instances
   *   <li>{@linkplain #dependencies() dependencies}
   * </ul>
   */
  Set<ComponentRequirement> requirements() {
    return requirements.get();
  }

  /**
   * This component's {@linkplain #dependencies() dependencies} keyed by each provision or
   * production method defined by that dependency. Note that the dependencies' types are not simply
   * the enclosing type of the method; a method may be declared by a supertype of the actual
   * dependency.
   */
  public Map<ExecutableElement, ComponentRequirement> dependenciesByDependencyMethod() {
    return dependenciesByDependencyMethod;
  }

  /** The {@linkplain #dependencies() component dependency} that defines a method. */
  public ComponentRequirement getDependencyThatDefinesMethod(Element method) {
    checkArgument(
        method instanceof ExecutableElement, "method must be an executable element: %s", method);
    return checkNotNull(
        dependenciesByDependencyMethod().get(method), "no dependency implements %s", method);
  }

  /** The scopes of the component. */
  public Set<Scope> scopes() {
    return scopes;
  }

  /**
   * All {@link Subcomponent}s which are direct children of this component. This includes
   * subcomponents installed from {@link Module#subcomponents()} as well as subcomponent {@linkplain
   * #childComponentsDeclaredByFactoryMethods() factory methods} and {@linkplain
   * #childComponentsDeclaredByBuilderEntryPoints() builder methods}.
   */
  public Set<ComponentDescriptor> childComponents() {
    return ImmutableSet.<ComponentDescriptor>builder()
        .addAll(childComponentsDeclaredByFactoryMethods().values())
        .addAll(childComponentsDeclaredByBuilderEntryPoints().values())
        .addAll(childComponentsDeclaredByModules())
        .build();
  }

  /**
   * All {@linkplain Subcomponent direct child} components that are declared by a {@linkplain
   * Module#subcomponents() module's subcomponents}.
   */
  Set<ComponentDescriptor> childComponentsDeclaredByModules() {
    return childComponentsDeclaredByModules;
  }

  /**
   * All {@linkplain Subcomponent direct child} components that are declared by a subcomponent
   * factory method.
   */
  public ImmutableBiMap<ComponentDescriptor.ComponentMethodDescriptor, ComponentDescriptor>
  childComponentsDeclaredByFactoryMethods() {
    return childComponentsDeclaredByFactoryMethods;
  }

  /** Returns a map of {@link #childComponents()} indexed by {@link #typeElement()}. */
  public Map<TypeElement, ComponentDescriptor> childComponentsByElement() {
    return childComponentsByElement.get();
  }

  /** Returns the factory method that declares a child component. */
  Optional<ComponentMethodDescriptor> getFactoryMethodForChildComponent(
      ComponentDescriptor childComponent) {
    return Optional.ofNullable(
        childComponentsDeclaredByFactoryMethods().inverse().get(childComponent));
  }

  /**
   * All {@linkplain Subcomponent direct child} components that are declared by a subcomponent
   * builder method.
   */
  ImmutableBiMap<ComponentDescriptor.ComponentMethodDescriptor, ComponentDescriptor>
  childComponentsDeclaredByBuilderEntryPoints() {
    return childComponentsDeclaredByBuilderEntryPoints;
  }

  private final Supplier<ImmutableMap<TypeElement, ComponentDescriptor>>
      childComponentsByBuilderType =
      Suppliers.memoize(
          () ->
              childComponents().stream()
                  .filter(child -> child.creatorDescriptor().isPresent())
                  .collect(
                      toImmutableMap(
                          child -> child.creatorDescriptor().get().typeElement(),
                          child -> child)));

  /** Returns the child component with the given builder type. */
  ComponentDescriptor getChildComponentWithBuilderType(TypeElement builderType) {
    return checkNotNull(
        childComponentsByBuilderType.get().get(builderType),
        "no child component found for builder type %s",
        builderType.getQualifiedName());
  }

  public Set<ComponentDescriptor.ComponentMethodDescriptor> componentMethods() {
    return componentMethods;
  }

  /** Returns the first component method associated with this binding request, if one exists. */
  public Optional<ComponentMethodDescriptor> firstMatchingComponentMethod(BindingRequest request) {
    return Optional.ofNullable(firstMatchingComponentMethods().get(request));
  }

  Map<BindingRequest, ComponentMethodDescriptor> firstMatchingComponentMethods() {
    return firstMatchingComponentMethods.get();
  }

  /** The entry point methods on the component type. Each has a {@link DependencyRequest}. */
  public Set<ComponentMethodDescriptor> entryPointMethods() {
    return componentMethods()
        .stream()
        .filter(method -> method.dependencyRequest().isPresent())
        .collect(toImmutableSet());
  }

  // TODO(gak): Consider making this non-optional and revising the
  // interaction between the spec & generation

  /** Returns a descriptor for the creator type for this component type, if the user defined one. */
  public Optional<ComponentCreatorDescriptor> creatorDescriptor() {
    return creatorDescriptor;
  }

  /**
   * Returns {@code true} for components that have a creator, either because the user {@linkplain
   * #creatorDescriptor() specified one} or because it's a top-level component with an implicit
   * builder.
   */
  public boolean hasCreator() {
    return !isSubcomponent() || creatorDescriptor().isPresent();
  }

  /**
   * Returns the {@link CancellationPolicy} for this component, or an empty optional if either the
   * component is not a production component or no {@code CancellationPolicy} annotation is present.
   */
  public Optional<CancellationPolicy> cancellationPolicy() {
    return isProduction()
        ? Optional.ofNullable(typeElement().getAnnotation(CancellationPolicy.class))
        : Optional.empty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ComponentDescriptor that = (ComponentDescriptor) o;
    return annotation.equals(that.annotation) && typeElement.equals(that.typeElement);
  }

  @Override
  public int hashCode() {
    return hashCode.getAsInt();
  }

  /** A component method. */
  public static final class ComponentMethodDescriptor {

    private final ExecutableElement methodElement;
    private final Optional<DependencyRequest> dependencyRequest;
    private final Optional<ComponentDescriptor> subcomponent;

    private ComponentMethodDescriptor(
        ExecutableElement methodElement,
        Optional<DependencyRequest> dependencyRequest,
        Optional<ComponentDescriptor> subcomponent) {
      this.methodElement = methodElement;
      this.dependencyRequest = dependencyRequest;
      this.subcomponent = subcomponent;
    }
    /** The method itself. Note that this may be declared on a supertype of the component. */
    public ExecutableElement methodElement() {
      return methodElement;
    }

    /**
     * The dependency request for production, provision, and subcomponent creator methods. Absent
     * for subcomponent factory methods.
     */
    public Optional<DependencyRequest> dependencyRequest() {
      return dependencyRequest;
    }

    /** The subcomponent for subcomponent factory methods and subcomponent creator methods. */
    public Optional<ComponentDescriptor> subcomponent() {
      return subcomponent;
    }

    /**
     * Returns the return type of {@link #methodElement()} as resolved in the {@link
     * ComponentDescriptor#typeElement() component type}. If there are no type variables in the
     * return type, this is the equivalent of {@code methodElement().getReturnType()}.
     */
    public TypeMirror resolvedReturnType(DaggerTypes types) {
      checkState(dependencyRequest().isPresent());

      TypeMirror returnType = methodElement().getReturnType();
      if (returnType.getKind().isPrimitive() || returnType.getKind().equals(VOID)) {
        return returnType;
      }
      return BindingRequest.bindingRequest(dependencyRequest().get())
          .requestedType(dependencyRequest().get().key().type(), types);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ComponentMethodDescriptor that = (ComponentMethodDescriptor) o;
      return methodElement.equals(that.methodElement)
          && dependencyRequest.equals(that.dependencyRequest)
          && subcomponent.equals(that.subcomponent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(methodElement,
          dependencyRequest,
          subcomponent);
    }

    /** A {@link ComponentMethodDescriptor}builder for a method. */
    public static Builder builder(ExecutableElement method) {
      return new Builder(method);
    }

    /** A builder of {@link ComponentMethodDescriptor}s. */
    public static final class Builder {
      private final ExecutableElement methodElement;
      private Optional<DependencyRequest> dependencyRequest = Optional.empty();
      private Optional<ComponentDescriptor> subcomponent = Optional.empty();

      private Builder(ExecutableElement methodElement) {
        this.methodElement = methodElement;
      }

      /** @see ComponentMethodDescriptor#dependencyRequest() */
      public Builder dependencyRequest(DependencyRequest dependencyRequest) {
        this.dependencyRequest = Optional.of(dependencyRequest);
        return this;
      }

      /** @see ComponentMethodDescriptor#subcomponent() */
      public Builder subcomponent(ComponentDescriptor subcomponent) {
        this.subcomponent = Optional.of(subcomponent);
        return this;
      }

      /** Builds the descriptor. */
      public ComponentDescriptor.ComponentMethodDescriptor build() {
        if (this.methodElement == null) {
          String missing = " methodElement";
          throw new IllegalStateException("Missing required properties:" + missing);
        }
        return new ComponentMethodDescriptor(
            this.methodElement,
            this.dependencyRequest,
            this.subcomponent);
      }
    }
  }

  /** No-argument methods defined on {@link Object} that are ignored for contribution. */
  private static final Set<String> NON_CONTRIBUTING_OBJECT_METHOD_NAMES =
      Set.of("toString", "hashCode", "clone", "getClass");

  /**
   * Returns {@code true} if a method could be a component entry point but not a members-injection
   * method.
   */
  static boolean isComponentContributionMethod(DaggerElements elements, ExecutableElement method) {
    return method.getParameters().isEmpty()
        && !method.getReturnType().getKind().equals(VOID)
        && !elements.getTypeElement(Object.class).equals(method.getEnclosingElement())
        && !NON_CONTRIBUTING_OBJECT_METHOD_NAMES.contains(method.getSimpleName().toString());
  }
}
