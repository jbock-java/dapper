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

import static dagger.internal.codegen.base.Suppliers.memoizeInt;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.type.TypeKind.VOID;

import dagger.Component;
import dagger.Module;
import dagger.Subcomponent;
import dagger.internal.codegen.base.ComponentAnnotation;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.xprocessing.XConverters;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.model.DependencyRequest;
import dagger.model.Scope;
import io.jbock.javapoet.TypeName;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

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

  // This is required temporarily during the XProcessing migration to use toXProcessing().
  private final XProcessingEnv processingEnv;
  private final ComponentAnnotation annotation;
  private final XTypeElement typeElement;
  private final Set<ComponentRequirement> dependencies;
  private final Set<ModuleDescriptor> modules;
  private final Map<XMethodElement, ComponentRequirement> dependenciesByDependencyMethod;
  private final Set<Scope> scopes;
  private final Set<ComponentDescriptor> childComponentsDeclaredByModules;
  private final Map<ComponentDescriptor.ComponentMethodDescriptor, ComponentDescriptor> childComponentsDeclaredByFactoryMethods;
  private final Map<ComponentDescriptor.ComponentMethodDescriptor, ComponentDescriptor> childComponentsDeclaredByBuilderEntryPoints;
  private final Set<ComponentDescriptor.ComponentMethodDescriptor> componentMethods;
  private final Optional<ComponentCreatorDescriptor> creatorDescriptor;

  ComponentDescriptor(
      XProcessingEnv processingEnv,
      ComponentAnnotation annotation,
      XTypeElement typeElement,
      Set<ComponentRequirement> dependencies,
      Set<ModuleDescriptor> modules,
      Map<XMethodElement, ComponentRequirement> dependenciesByDependencyMethod,
      Set<Scope> scopes,
      Set<ComponentDescriptor> childComponentsDeclaredByModules,
      Map<ComponentMethodDescriptor, ComponentDescriptor> childComponentsDeclaredByFactoryMethods,
      Map<ComponentMethodDescriptor, ComponentDescriptor> childComponentsDeclaredByBuilderEntryPoints,
      Set<ComponentMethodDescriptor> componentMethods,
      Optional<ComponentCreatorDescriptor> creatorDescriptor) {
    this.processingEnv = processingEnv;
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
    Set<ComponentRequirement> requirements = new LinkedHashSet<>();
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
            .orElse(Set.of()));
    return requirements;
  });

  private final IntSupplier hashCode = memoizeInt(() -> {
    // TODO(b/122962745): Only use typeElement().hashCode()
    return Objects.hash(typeElement(), annotation());
  });

  private final Supplier<Map<BindingRequest, ComponentMethodDescriptor>> firstMatchingComponentMethods = Suppliers.memoize(() -> {
    Map<BindingRequest, ComponentMethodDescriptor> methods = new LinkedHashMap<>();
    for (ComponentMethodDescriptor method : entryPointMethods()) {
      methods.putIfAbsent(BindingRequest.bindingRequest(method.dependencyRequest().orElseThrow()), method);
    }
    return methods;
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
  public XTypeElement typeElement() {
    return typeElement;
  }

  /**
   * The set of component dependencies listed in {@link Component#dependencies}.
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
  public Map<XMethodElement, ComponentRequirement> dependenciesByDependencyMethod() {
    return dependenciesByDependencyMethod;
  }

  /** The {@linkplain #dependencies() component dependency} that defines a method. */
  public ComponentRequirement getDependencyThatDefinesMethod(Element javaMethod) {
    XElement method = XConverters.toXProcessing(javaMethod, processingEnv);
    Preconditions.checkArgument(
        method instanceof XMethodElement, "method must be an executable element: %s", method);
    return requireNonNull(
        dependenciesByDependencyMethod().get(method), () -> String.format("no dependency implements %s", method));
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
    Set<ComponentDescriptor> result = new LinkedHashSet<>();
    result.addAll(childComponentsDeclaredByFactoryMethods().values());
    result.addAll(childComponentsDeclaredByBuilderEntryPoints().values());
    result.addAll(childComponentsDeclaredByModules());
    return result;
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
  public Map<ComponentDescriptor.ComponentMethodDescriptor, ComponentDescriptor>
  childComponentsDeclaredByFactoryMethods() {
    return childComponentsDeclaredByFactoryMethods;
  }

  /** Returns the factory method that declares a child component. */
  Optional<ComponentMethodDescriptor> getFactoryMethodForChildComponent(
      ComponentDescriptor childComponent) {
    return childComponentsDeclaredByFactoryMethods().entrySet()
        .stream()
        .filter(e -> e.getValue().equals(childComponent))
        .map(Map.Entry::getKey)
        .findFirst();
  }

  /**
   * All {@linkplain Subcomponent direct child} components that are declared by a subcomponent
   * builder method.
   */
  Map<ComponentDescriptor.ComponentMethodDescriptor, ComponentDescriptor>
  childComponentsDeclaredByBuilderEntryPoints() {
    return childComponentsDeclaredByBuilderEntryPoints;
  }

  private final Supplier<Map<TypeElement, ComponentDescriptor>>
      childComponentsByBuilderType =
      Suppliers.memoize(
          () ->
              childComponents().stream()
                  .filter(child -> child.creatorDescriptor().isPresent())
                  .collect(
                      toImmutableMap(
                          child -> child.creatorDescriptor().orElseThrow().typeElement().toJavac(),
                          child -> child)));

  /** Returns the child component with the given builder type. */
  ComponentDescriptor getChildComponentWithBuilderType(TypeElement builderType) {
    return Objects.requireNonNull(
        childComponentsByBuilderType.get().get(builderType),
        () -> String.format("no child component found for builder type %s",
            builderType.getQualifiedName()));
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
      Preconditions.checkState(dependencyRequest().isPresent());

      TypeMirror returnType = methodElement().getReturnType();
      if (returnType.getKind().isPrimitive() || returnType.getKind().equals(VOID)) {
        return returnType;
      }
      return BindingRequest.bindingRequest(dependencyRequest().get())
          .requestedType(dependencyRequest().get().key().type().java(), types);
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
    public static Builder builder(XMethodElement method) {
      return new Builder(method);
    }

    /** A builder of {@link ComponentMethodDescriptor}s. */
    public static final class Builder {
      private final XMethodElement methodElement;
      private Optional<DependencyRequest> dependencyRequest = Optional.empty();
      private Optional<ComponentDescriptor> subcomponent = Optional.empty();

      private Builder(XMethodElement methodElement) {
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
        if (methodElement == null) {
          String missing = " methodElement";
          throw new IllegalStateException("Missing required properties:" + missing);
        }
        return new ComponentMethodDescriptor(
            methodElement.toJavac(),
            dependencyRequest,
            subcomponent);
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
        && !elements.getTypeElement(TypeName.OBJECT).equals(method.getEnclosingElement())
        && !NON_CONTRIBUTING_OBJECT_METHOD_NAMES.contains(method.getSimpleName().toString());
  }
}
