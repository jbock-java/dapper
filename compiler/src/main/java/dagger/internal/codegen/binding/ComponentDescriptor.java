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

import static dagger.internal.codegen.base.Preconditions.checkArgument;
import static dagger.internal.codegen.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.base.Preconditions.checkState;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerTypes.isFutureType;
import static dagger.internal.codegen.xprocessing.XElement.isMethod;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XType.isVoid;
import static dagger.internal.codegen.xprocessing.XTypes.isPrimitive;

import dagger.internal.codegen.base.ComponentAnnotation;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.collect.ImmutableBiMap;
import dagger.internal.codegen.collect.ImmutableMap;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.collect.Maps;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Scope;
import io.jbock.auto.value.AutoValue;
import io.jbock.auto.value.extension.memoized.Memoized;
import io.jbock.javapoet.TypeName;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A component declaration.
 *
 * <p>Represents one type annotated with {@code @Component}, {@code Subcomponent},
 * {@code @ProductionComponent}, or {@code @ProductionSubcomponent}.
 *
 * <p>When validating bindings installed in modules, a {@code ComponentDescriptor} can also
 * represent a synthetic component for the module, where there is an entry point for each binding in
 * the module.
 */
@AutoValue
public abstract class ComponentDescriptor {
  /** Creates a {@code ComponentDescriptor}. */
  static ComponentDescriptor create(
      ComponentAnnotation componentAnnotation,
      XTypeElement component,
      ImmutableSet<ComponentRequirement> componentDependencies,
      ImmutableSet<ModuleDescriptor> transitiveModules,
      ImmutableMap<XMethodElement, ComponentRequirement> dependenciesByDependencyMethod,
      ImmutableSet<Scope> scopes,
      ImmutableSet<ComponentDescriptor> subcomponentsFromModules,
      ImmutableBiMap<ComponentMethodDescriptor, ComponentDescriptor> subcomponentsByFactoryMethod,
      ImmutableBiMap<ComponentMethodDescriptor, ComponentDescriptor> subcomponentsByBuilderMethod,
      ImmutableSet<ComponentMethodDescriptor> componentMethods,
      Optional<ComponentCreatorDescriptor> creator) {
    ComponentDescriptor descriptor =
        new AutoValue_ComponentDescriptor(
            componentAnnotation,
            component,
            componentDependencies,
            transitiveModules,
            dependenciesByDependencyMethod,
            scopes,
            subcomponentsFromModules,
            subcomponentsByFactoryMethod,
            subcomponentsByBuilderMethod,
            componentMethods,
            creator);
    return descriptor;
  }

  /** The annotation that specifies that {@code #typeElement()} is a component. */
  public abstract ComponentAnnotation annotation();

  /** Returns {@code true} if this is a subcomponent. */
  public final boolean isSubcomponent() {
    return annotation().isSubcomponent();
  }

  /**
   * Returns {@code true} if this is a production component or subcomponent, or a
   * {@code @ProducerModule} when doing module binding validation.
   */
  public final boolean isProduction() {
    return annotation().isProduction();
  }

  /**
   * Returns {@code true} if this is a real component, and not a fictional one used to validate
   * module bindings.
   */
  public final boolean isRealComponent() {
    return annotation().isRealComponent();
  }

  /**
   * The element that defines the component. This is the element to which the {@code #annotation()}
   * was applied.
   */
  public abstract XTypeElement typeElement();

  /**
   * The set of component dependencies listed in {@code Component#dependencies} or {@code
   * dagger.producers.ProductionComponent#dependencies()}.
   */
  public abstract ImmutableSet<ComponentRequirement> dependencies();

  /** The non-abstract {@code #modules()} and the {@code #dependencies()}. */
  public final ImmutableSet<ComponentRequirement> dependenciesAndConcreteModules() {
    return Stream.concat(
            moduleTypes().stream()
                .filter(dep -> !dep.isAbstract())
                .map(module -> ComponentRequirement.forModule(module.getType())),
            dependencies().stream())
        .collect(toImmutableSet());
  }

  /**
   * The {@code ModuleDescriptor modules} declared in {@code Component#modules()} and reachable by
   * traversing {@code Module#includes()}.
   */
  public abstract ImmutableSet<ModuleDescriptor> modules();

  /** The types of the {@code #modules()}. */
  public final ImmutableSet<XTypeElement> moduleTypes() {
    return modules().stream().map(ModuleDescriptor::moduleElement).collect(toImmutableSet());
  }

  /**
   * The types for which the component will need instances if all of its bindings are used. For the
   * types the component will need in a given binding graph, use {@code
   * BindingGraph#componentRequirements()}.
   *
   * <ul>
   *   <li>{@code #modules()} modules} with concrete instance bindings
   *   <li>Bound instances
   *   <li>{@code #dependencies() dependencies}
   * </ul>
   */
  @Memoized
  ImmutableSet<ComponentRequirement> requirements() {
    ImmutableSet.Builder<ComponentRequirement> requirements = ImmutableSet.builder();
    modules().stream()
        .filter(
            module ->
                module.bindings().stream().anyMatch(ContributionBinding::requiresModuleInstance))
        .map(module -> ComponentRequirement.forModule(module.moduleElement().getType()))
        .forEach(requirements::add);
    requirements.addAll(dependencies());
    requirements.addAll(
        creatorDescriptor()
            .map(ComponentCreatorDescriptor::boundInstanceRequirements)
            .orElse(ImmutableSet.of()));
    return requirements.build();
  }

  /**
   * This component's {@code #dependencies() dependencies} keyed by each provision or
   * production method defined by that dependency. Note that the dependencies' types are not simply
   * the enclosing type of the method; a method may be declared by a supertype of the actual
   * dependency.
   */
  public abstract ImmutableMap<XMethodElement, ComponentRequirement>
      dependenciesByDependencyMethod();

  /** The {@code #dependencies() component dependency} that defines a method. */
  public final ComponentRequirement getDependencyThatDefinesMethod(XElement method) {
    checkArgument(isMethod(method), "method must be an executable element: %s", method);
    checkState(
        dependenciesByDependencyMethod().containsKey(method),
        "no dependency implements %s",
        method);
    return dependenciesByDependencyMethod().get(method);
  }

  /** The scopes of the component. */
  public abstract ImmutableSet<Scope> scopes();

  /**
   * All {@code Subcomponent}s which are direct children of this component. This includes
   * subcomponents installed from {@code Module#subcomponents()} as well as subcomponent {@code
   * #childComponentsDeclaredByFactoryMethods() factory methods} and {@code
   * #childComponentsDeclaredByBuilderEntryPoints() builder methods}.
   */
  public final ImmutableSet<ComponentDescriptor> childComponents() {
    return ImmutableSet.<ComponentDescriptor>builder()
        .addAll(childComponentsDeclaredByFactoryMethods().values())
        .addAll(childComponentsDeclaredByBuilderEntryPoints().values())
        .addAll(childComponentsDeclaredByModules())
        .build();
  }

  /**
   * All {@code Subcomponent direct child} components that are declared by a {@code
   * Module#subcomponents() module's subcomponents}.
   */
  abstract ImmutableSet<ComponentDescriptor> childComponentsDeclaredByModules();

  /**
   * All {@code Subcomponent direct child} components that are declared by a subcomponent
   * factory method.
   */
  public abstract ImmutableBiMap<ComponentMethodDescriptor, ComponentDescriptor>
      childComponentsDeclaredByFactoryMethods();

  /** Returns a map of {@code #childComponents()} indexed by {@code #typeElement()}. */
  @Memoized
  public ImmutableMap<XTypeElement, ComponentDescriptor> childComponentsByElement() {
    return Maps.uniqueIndex(childComponents(), ComponentDescriptor::typeElement);
  }

  /** Returns the factory method that declares a child component. */
  final Optional<ComponentMethodDescriptor> getFactoryMethodForChildComponent(
      ComponentDescriptor childComponent) {
    return Optional.ofNullable(
        childComponentsDeclaredByFactoryMethods().inverse().get(childComponent));
  }

  /**
   * All {@code Subcomponent direct child} components that are declared by a subcomponent
   * builder method.
   */
  abstract ImmutableBiMap<ComponentMethodDescriptor, ComponentDescriptor>
      childComponentsDeclaredByBuilderEntryPoints();

  private final Supplier<ImmutableMap<XTypeElement, ComponentDescriptor>>
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
  final ComponentDescriptor getChildComponentWithBuilderType(XTypeElement builderType) {
    return checkNotNull(
        childComponentsByBuilderType.get().get(builderType),
        "no child component found for builder type %s",
        builderType.getQualifiedName());
  }

  public abstract ImmutableSet<ComponentMethodDescriptor> componentMethods();

  /** Returns the first component method associated with this binding request, if one exists. */
  public Optional<ComponentMethodDescriptor> firstMatchingComponentMethod(BindingRequest request) {
    return Optional.ofNullable(firstMatchingComponentMethods().get(request));
  }

  @Memoized
  ImmutableMap<BindingRequest, ComponentMethodDescriptor>
      firstMatchingComponentMethods() {
    Map<BindingRequest, ComponentMethodDescriptor> methods = new HashMap<>();
    for (ComponentMethodDescriptor method : entryPointMethods()) {
      methods.putIfAbsent(BindingRequest.bindingRequest(method.dependencyRequest().get()), method);
    }
    return ImmutableMap.copyOf(methods);
  }

  /** The entry point methods on the component type. Each has a {@code DependencyRequest}. */
  public final ImmutableSet<ComponentMethodDescriptor> entryPointMethods() {
    return componentMethods()
        .stream()
        .filter(method -> method.dependencyRequest().isPresent())
        .collect(toImmutableSet());
  }

  // TODO(gak): Consider making this non-optional and revising the
  // interaction between the spec & generation
  /** Returns a descriptor for the creator type for this component type, if the user defined one. */
  public abstract Optional<ComponentCreatorDescriptor> creatorDescriptor();

  /**
   * Returns {@code true} for components that have a creator, either because the user {@code
   * #creatorDescriptor() specified one} or because it's a top-level component with an implicit
   * builder.
   */
  public final boolean hasCreator() {
    return !isSubcomponent() || creatorDescriptor().isPresent();
  }

  @Memoized
  @Override
  public int hashCode() {
    // TODO(b/122962745): Only use typeElement().hashCode()
    return Objects.hash(typeElement(), annotation());
  }

  // TODO(ronshapiro): simplify the equality semantics
  @Override
  public abstract boolean equals(Object obj);

  /** A component method. */
  @AutoValue
  public abstract static class ComponentMethodDescriptor {
    /** The method itself. Note that this may be declared on a supertype of the component. */
    public abstract XMethodElement methodElement();

    /**
     * The dependency request for production, provision, and subcomponent creator methods. Absent
     * for subcomponent factory methods.
     */
    public abstract Optional<DependencyRequest> dependencyRequest();

    /** The subcomponent for subcomponent factory methods and subcomponent creator methods. */
    public abstract Optional<ComponentDescriptor> subcomponent();

    /**
     * Returns the return type of {@code #methodElement()} as resolved in the {@code
     * ComponentDescriptor#typeElement() component type}. If there are no type variables in the
     * return type, this is the equivalent of {@code methodElement().getReturnType()}.
     */
    public XType resolvedReturnType(XProcessingEnv processingEnv) {
      checkState(dependencyRequest().isPresent());

      XType returnType = methodElement().getReturnType();
      if (isPrimitive(returnType) || isVoid(returnType)) {
        return returnType;
      }
      return BindingRequest.bindingRequest(dependencyRequest().get())
          .requestedType(dependencyRequest().get().key().type().xprocessing(), processingEnv);
    }

    /** A {@code ComponentMethodDescriptor}builder for a method. */
    public static Builder builder(XMethodElement method) {
      return new AutoValue_ComponentDescriptor_ComponentMethodDescriptor.Builder()
          .methodElement(method);
    }

    /** A builder of {@code ComponentMethodDescriptor}s. */
    @AutoValue.Builder
    public interface Builder {
      /** @see ComponentMethodDescriptor#methodElement() */
      Builder methodElement(XMethodElement methodElement);

      /** @see ComponentMethodDescriptor#dependencyRequest() */
      Builder dependencyRequest(DependencyRequest dependencyRequest);

      /** @see ComponentMethodDescriptor#subcomponent() */
      Builder subcomponent(ComponentDescriptor subcomponent);

      /** Builds the descriptor. */
      ComponentMethodDescriptor build();
    }
  }

  /** No-argument methods defined on {@code Object} that are ignored for contribution. */
  private static final ImmutableSet<String> NON_CONTRIBUTING_OBJECT_METHOD_NAMES =
      ImmutableSet.of("toString", "hashCode", "clone", "getClass");

  /**
   * Returns {@code true} if a method could be a component entry point but not a members-injection
   * method.
   */
  static boolean isComponentContributionMethod(XMethodElement method) {
    return method.getParameters().isEmpty()
        && !isVoid(method.getReturnType())
        && !method.getEnclosingElement().getClassName().equals(TypeName.OBJECT)
        && !NON_CONTRIBUTING_OBJECT_METHOD_NAMES.contains(getSimpleName(method));
  }

  /** Returns {@code true} if a method could be a component production entry point. */
  static boolean isComponentProductionMethod(XMethodElement method) {
    return isComponentContributionMethod(method) && isFutureType(method.getReturnType());
  }
}
