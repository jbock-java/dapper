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

import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.auto.common.MoreTypes.isType;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static dagger.internal.codegen.base.RequestKinds.getRequestKind;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedFactoryType;
import static dagger.internal.codegen.binding.ComponentDescriptor.isComponentContributionMethod;
import static dagger.model.BindingKind.ASSISTED_INJECTION;
import static dagger.model.BindingKind.DELEGATE;
import static dagger.model.BindingKind.INJECTION;
import static dagger.model.BindingKind.OPTIONAL;
import static dagger.model.BindingKind.SUBCOMPONENT_CREATOR;
import static dagger.model.RequestKind.MEMBERS_INJECTION;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.isEqual;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.MoreTypes;
import dagger.MembersInjector;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.Keys;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.OptionalType;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.extension.DaggerStreams;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.Scope;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

/** A factory for {@link BindingGraph} objects. */
@Singleton
public final class BindingGraphFactory implements ClearableCache {

  private final DaggerElements elements;
  private final InjectBindingRegistry injectBindingRegistry;
  private final KeyFactory keyFactory;
  private final BindingFactory bindingFactory;
  private final BindingGraphConverter bindingGraphConverter;
  private final Map<Key, Set<Key>> keysMatchingRequestCache = new HashMap<>();
  private final CompilerOptions compilerOptions;

  @Inject
  BindingGraphFactory(
      DaggerElements elements,
      InjectBindingRegistry injectBindingRegistry,
      KeyFactory keyFactory,
      BindingFactory bindingFactory,
      BindingGraphConverter bindingGraphConverter,
      CompilerOptions compilerOptions) {
    this.elements = elements;
    this.injectBindingRegistry = injectBindingRegistry;
    this.keyFactory = keyFactory;
    this.bindingFactory = bindingFactory;
    this.bindingGraphConverter = bindingGraphConverter;
    this.compilerOptions = compilerOptions;
  }

  /**
   * Creates a binding graph for a component.
   *
   * @param createFullBindingGraph if {@code true}, the binding graph will include all bindings;
   *     otherwise it will include only bindings reachable from at least one entry point
   */
  public BindingGraph create(
      ComponentDescriptor componentDescriptor, boolean createFullBindingGraph) {
    return bindingGraphConverter.convert(
        createLegacyBindingGraph(Optional.empty(), componentDescriptor, createFullBindingGraph),
        createFullBindingGraph);
  }

  private LegacyBindingGraph createLegacyBindingGraph(
      Optional<Resolver> parentResolver,
      ComponentDescriptor componentDescriptor,
      boolean createFullBindingGraph) {
    Set<ContributionBinding> explicitBindingsBuilder = new LinkedHashSet<>();
    Set<DelegateDeclaration> delegatesBuilder = new LinkedHashSet<>();
    Set<OptionalBindingDeclaration> optionalsBuilder = new LinkedHashSet<>();

    if (componentDescriptor.isRealComponent()) {
      // binding for the component itself
      explicitBindingsBuilder.add(
          bindingFactory.componentBinding(componentDescriptor.typeElement()));
    }

    // Collect Component dependencies.
    for (ComponentRequirement dependency : componentDescriptor.dependencies()) {
      explicitBindingsBuilder.add(bindingFactory.componentDependencyBinding(dependency));
      List<ExecutableElement> dependencyMethods =
          methodsIn(elements.getAllMembers(dependency.typeElement()));

      // Within a component dependency, we want to allow the same method to appear multiple
      // times assuming it is the exact same method. We do this by tracking a set of bindings
      // we've already added with the binding element removed since that is the only thing
      // allowed to differ.
      Map<String, Set<ContributionBinding>> dedupeBindings = new HashMap<>();
      for (ExecutableElement method : dependencyMethods) {
        // MembersInjection methods aren't "provided" explicitly, so ignore them.
        if (isComponentContributionMethod(elements, method)) {
          ContributionBinding binding = bindingFactory.componentDependencyMethodBinding(
              method);
          int previousSize = dedupeBindings.getOrDefault(method.getSimpleName().toString(), Set.of()).size();
          if (dedupeBindings.merge(
              method.getSimpleName().toString(),
              // Remove the binding element since we know that will be different, but everything
              // else we want to be the same to consider it a duplicate.
              Set.of(binding.toBuilder().clearBindingElement().build()),
              Util::mutableUnion).size() > previousSize) {
            explicitBindingsBuilder.add(binding);
          }
        }
      }
    }

    // Collect bindings on the creator.
    componentDescriptor
        .creatorDescriptor()
        .ifPresent(
            creatorDescriptor ->
                creatorDescriptor.boundInstanceRequirements().stream()
                    .map(
                        requirement ->
                            bindingFactory.boundInstanceBinding(
                                requirement, creatorDescriptor.elementForRequirement(requirement)))
                    .forEach(explicitBindingsBuilder::add));

    componentDescriptor
        .childComponentsDeclaredByBuilderEntryPoints()
        .forEach(
            (builderEntryPoint, childComponent) -> {
              if (!componentDescriptor
                  .childComponentsDeclaredByModules()
                  .contains(childComponent)) {
                explicitBindingsBuilder.add(
                    bindingFactory.subcomponentCreatorBinding(
                        builderEntryPoint.methodElement(), componentDescriptor.typeElement()));
              }
            });

    Set<SubcomponentDeclaration> subcomponentDeclarations = new LinkedHashSet<>();

    // Collect transitive module bindings and multibinding declarations.
    for (ModuleDescriptor moduleDescriptor : componentDescriptor.modules()) {
      explicitBindingsBuilder.addAll(moduleDescriptor.bindings());
      subcomponentDeclarations.addAll(moduleDescriptor.subcomponentDeclarations());
      delegatesBuilder.addAll(moduleDescriptor.delegateDeclarations());
      optionalsBuilder.addAll(moduleDescriptor.optionalDeclarations());
    }

    final Resolver requestResolver =
        new Resolver(
            parentResolver,
            componentDescriptor,
            indexBindingDeclarationsByKey(explicitBindingsBuilder),
            indexBindingDeclarationsByKey(subcomponentDeclarations),
            indexBindingDeclarationsByKey(delegatesBuilder),
            indexBindingDeclarationsByKey(optionalsBuilder));

    componentDescriptor.entryPointMethods().stream()
        .map(method -> method.dependencyRequest().orElseThrow())
        .forEach(
            entryPoint -> {
              if (entryPoint.kind().equals(MEMBERS_INJECTION)) {
                requestResolver.resolveMembersInjection(entryPoint.key());
              } else {
                requestResolver.resolve(entryPoint.key());
              }
            });

    if (createFullBindingGraph) {
      // Resolve the keys for all bindings in all modules, stripping any multibinding contribution
      // identifier so that the multibinding itself is resolved.
      componentDescriptor.modules().stream()
          .flatMap(module -> module.allBindingKeys().stream())
          .forEach(requestResolver::resolve);
    }

    // Resolve all bindings for subcomponents, creating subgraphs for all subcomponents that have
    // been detected during binding resolution. If a binding for a subcomponent is never resolved,
    // no BindingGraph will be created for it and no implementation will be generated. This is
    // done in a queue since resolving one subcomponent might resolve a key for a subcomponent
    // from a parent graph. This is done until no more new subcomponents are resolved.
    Set<ComponentDescriptor> resolvedSubcomponents = new HashSet<>();
    List<LegacyBindingGraph> subgraphs = new ArrayList<>();
    while (!requestResolver.subcomponentsToResolve.isEmpty()) {
      ComponentDescriptor subcomponent = requestResolver.subcomponentsToResolve.remove();
      if (resolvedSubcomponents.add(subcomponent)) {
        subgraphs.add(
            createLegacyBindingGraph(
                Optional.of(requestResolver), subcomponent, createFullBindingGraph));
      }
    }

    return new LegacyBindingGraph(
        componentDescriptor,
        new LinkedHashMap<>(requestResolver.getResolvedContributionBindings()),
        new LinkedHashMap<>(requestResolver.getResolvedMembersInjectionBindings()),
        List.copyOf(subgraphs));
  }

  /** Indexes {@code bindingDeclarations} by {@link BindingDeclaration#key()}. */
  private static <T extends BindingDeclaration>
  Map<Key, Set<T>> indexBindingDeclarationsByKey(Set<T> declarations) {
    return declarations.stream().collect(Collectors.groupingBy(
        BindingDeclaration::key,
        LinkedHashMap::new,
        DaggerStreams.toImmutableSet()));
  }

  @Override
  public void clearCache() {
    keysMatchingRequestCache.clear();
  }

  private final class Resolver {
    final Optional<Resolver> parentResolver;
    final ComponentDescriptor componentDescriptor;
    final Map<Key, Set<ContributionBinding>> explicitBindings;
    final Set<ContributionBinding> explicitBindingsSet;
    final Map<Key, Set<SubcomponentDeclaration>> subcomponentDeclarations;
    final Map<Key, Set<DelegateDeclaration>> delegateDeclarations;
    final Map<Key, Set<OptionalBindingDeclaration>> optionalBindingDeclarations;
    final Map<Key, ResolvedBindings> resolvedContributionBindings = new LinkedHashMap<>();
    final Map<Key, ResolvedBindings> resolvedMembersInjectionBindings = new LinkedHashMap<>();
    final Deque<Key> cycleStack = new ArrayDeque<>();
    final Map<Key, Boolean> keyDependsOnLocalBindingsCache = new HashMap<>();
    final Map<Binding, Boolean> bindingDependsOnLocalBindingsCache = new HashMap<>();
    final Queue<ComponentDescriptor> subcomponentsToResolve = new ArrayDeque<>();

    Resolver(
        Optional<Resolver> parentResolver,
        ComponentDescriptor componentDescriptor,
        Map<Key, Set<ContributionBinding>> explicitBindings,
        Map<Key, Set<SubcomponentDeclaration>> subcomponentDeclarations,
        Map<Key, Set<DelegateDeclaration>> delegateDeclarations,
        Map<Key, Set<OptionalBindingDeclaration>> optionalBindingDeclarations) {
      this.parentResolver = parentResolver;
      this.componentDescriptor = requireNonNull(componentDescriptor);
      this.explicitBindings = requireNonNull(explicitBindings);
      this.explicitBindingsSet = explicitBindings.values().stream().flatMap(Set::stream).collect(Collectors.toCollection(LinkedHashSet::new));
      this.subcomponentDeclarations = requireNonNull(subcomponentDeclarations);
      this.delegateDeclarations = requireNonNull(delegateDeclarations);
      this.optionalBindingDeclarations = requireNonNull(optionalBindingDeclarations);
      subcomponentsToResolve.addAll(
          componentDescriptor.childComponentsDeclaredByFactoryMethods().values());
      subcomponentsToResolve.addAll(
          componentDescriptor.childComponentsDeclaredByBuilderEntryPoints().values());
    }

    /**
     * Returns the resolved contribution bindings for the given {@link Key}:
     *
     * <ul>
     *   <li>All explicit bindings for:
     *       <ul>
     *         <li>the requested key
     *         <li>{@code Set<T>} if the requested key's type is {@code Set<Produced<T>>}
     *         <li>{@code Map<K, Provider<V>>} if the requested key's type is {@code Map<K,
     *             Producer<V>>}.
     *       </ul>
     *   <li>An implicit {@code @Inject}-annotated constructor binding if there is one and
     *       there are no explicit bindings or synthetic bindings.
     * </ul>
     */
    ResolvedBindings lookUpBindings(Key requestKey) {
      Set<ContributionBinding> bindings = new LinkedHashSet<>();
      Set<OptionalBindingDeclaration> optionalBindingDeclarations = new LinkedHashSet<>();
      Set<SubcomponentDeclaration> subcomponentDeclarations = new LinkedHashSet<>();

      // Gather all bindings, multibindings, optional, and subcomponent declarations/contributions.
      Set<Key> keysMatchingRequest = keysMatchingRequest(requestKey);
      for (Resolver resolver : getResolverLineage()) {
        bindings.addAll(resolver.getLocalExplicitBindings(requestKey));

        for (Key key : keysMatchingRequest) {
          subcomponentDeclarations.addAll(resolver.subcomponentDeclarations.getOrDefault(key, Set.of()));
          // The optional binding declarations are keyed by the unwrapped type.
          keyFactory.unwrapOptional(key)
              .map(k -> resolver.optionalBindingDeclarations.getOrDefault(k, Set.of()))
              .ifPresent(optionalBindingDeclarations::addAll);
        }
      }

      // Add synthetic optional binding
      if (!optionalBindingDeclarations.isEmpty()) {
        bindings.add(
            bindingFactory.syntheticOptionalBinding(
                requestKey,
                getRequestKind(OptionalType.from(requestKey).valueType()),
                lookUpBindings(keyFactory.unwrapOptional(requestKey).orElseThrow()).bindings()));
      }

      // Add subcomponent creator binding
      if (!subcomponentDeclarations.isEmpty()) {
        ProvisionBinding binding =
            bindingFactory.subcomponentCreatorBinding(
                new LinkedHashSet<>(subcomponentDeclarations));
        bindings.add(binding);
        addSubcomponentToOwningResolver(binding);
      }

      // Add members injector binding
      if (isType(requestKey.type()) && isTypeOf(MembersInjector.class, requestKey.type())) {
        injectBindingRegistry
            .getOrFindMembersInjectorProvisionBinding(requestKey)
            .ifPresent(bindings::add);
      }

      // Add Assisted Factory binding
      if (isType(requestKey.type())
          && requestKey.type().getKind() == TypeKind.DECLARED
          && isAssistedFactoryType(asTypeElement(requestKey.type()))) {
        bindings.add(
            bindingFactory.assistedFactoryBinding(
                asTypeElement(requestKey.type()), Optional.of(requestKey.type())));
      }

      // If there are no bindings, add the implicit @Inject-constructed binding if there is one.
      if (bindings.isEmpty()) {
        injectBindingRegistry
            .getOrFindProvisionBinding(requestKey)
            .filter(this::isCorrectlyScopedInSubcomponent)
            .ifPresent(bindings::add);
      }

      return ResolvedBindings.forContributionBindings(
          requestKey,
          bindings.stream().collect(Collectors.groupingBy((ContributionBinding binding) -> getOwningComponent(requestKey, binding))),
          subcomponentDeclarations,
          optionalBindingDeclarations);
    }

    /**
     * Returns true if this binding graph resolution is for a subcomponent and the {@code @Inject}
     * binding's scope correctly matches one of the components in the current component ancestry.
     * If not, it means the binding is not owned by any of the currently known components, and will
     * be owned by a future ancestor (or, if never owned, will result in an incompatibly scoped
     * binding error at the root component).
     */
    private boolean isCorrectlyScopedInSubcomponent(ProvisionBinding binding) {
      Preconditions.checkArgument(binding.kind() == INJECTION || binding.kind() == ASSISTED_INJECTION);
      if (!rootComponent().isSubcomponent()
          || binding.scope().isEmpty()
          || binding.scope().orElseThrow().isReusable()) {
        return true;
      }

      Resolver owningResolver = getOwningResolver(binding).orElse(this);
      ComponentDescriptor owningComponent = owningResolver.componentDescriptor;
      return owningComponent.scopes().contains(binding.scope().orElseThrow());
    }

    private ComponentDescriptor rootComponent() {
      return parentResolver.map(Resolver::rootComponent).orElse(componentDescriptor);
    }

    /** Returns the resolved members injection bindings for the given {@link Key}. */
    ResolvedBindings lookUpMembersInjectionBinding(Key requestKey) {
      // no explicit deps for members injection, so just look it up
      Optional<MembersInjectionBinding> binding =
          injectBindingRegistry.getOrFindMembersInjectionBinding(requestKey);
      return binding.isPresent()
          ? ResolvedBindings.forMembersInjectionBinding(
          requestKey, componentDescriptor, binding.orElseThrow())
          : ResolvedBindings.noBindings(requestKey);
    }

    /**
     * When a binding is resolved for a {@link SubcomponentDeclaration}, adds corresponding {@link
     * ComponentDescriptor subcomponent} to a queue in the owning component's resolver. The queue
     * will be used to detect which subcomponents need to be resolved.
     */
    private void addSubcomponentToOwningResolver(ProvisionBinding subcomponentCreatorBinding) {
      Preconditions.checkArgument(subcomponentCreatorBinding.kind().equals(SUBCOMPONENT_CREATOR));
      Resolver owningResolver = getOwningResolver(subcomponentCreatorBinding).orElseThrow();

      TypeElement builderType = MoreTypes.asTypeElement(subcomponentCreatorBinding.key().type());
      owningResolver.subcomponentsToResolve.add(
          owningResolver.componentDescriptor.getChildComponentWithBuilderType(builderType));
    }

    /**
     * Profiling has determined that computing the keys matching {@code requestKey} has measurable
     * performance impact. It is called repeatedly (at least 3 times per key resolved per {@link
     * BindingGraph}. {@code javac}'s name-checking performance seems suboptimal (converting byte
     * strings to Strings repeatedly), and the matching keys creations relies on that. This also
     * ensures that the resulting keys have their hash codes cached on successive calls to this
     * method.
     *
     * <p>This caching may become obsolete if:
     *
     * <ul>
     *   <li>We decide to intern all {@link Key} instances
     *   <li>We fix javac's name-checking peformance (though we may want to keep this for older
     *       javac users)
     * </ul>
     */
    private Set<Key> keysMatchingRequest(Key requestKey) {
      return keysMatchingRequestCache.computeIfAbsent(
          requestKey, this::keysMatchingRequestUncached);
    }

    private Set<Key> keysMatchingRequestUncached(Key requestKey) {
      Set<Key> keys = new LinkedHashSet<>();
      keys.add(requestKey);
      keys.addAll(keyFactory.implicitFrameworkMapKeys(requestKey));
      return keys;
    }

    private Set<ContributionBinding> createDelegateBindings(
        Set<DelegateDeclaration> delegateDeclarations) {
      Set<ContributionBinding> builder = new LinkedHashSet<>();
      for (DelegateDeclaration delegateDeclaration : delegateDeclarations) {
        builder.add(createDelegateBinding(delegateDeclaration));
      }
      return builder;
    }

    /**
     * Creates one (and only one) delegate binding for a delegate declaration, based on the resolved
     * bindings of the right-hand-side of a {@link dagger.Binds} method. If there are duplicate
     * bindings for the dependency key, there should still be only one binding for the delegate key.
     */
    private ContributionBinding createDelegateBinding(DelegateDeclaration delegateDeclaration) {
      Key delegateKey = delegateDeclaration.delegateRequest().key();
      if (cycleStack.contains(delegateKey)) {
        return bindingFactory.unresolvedDelegateBinding(delegateDeclaration);
      }

      ResolvedBindings resolvedDelegate;
      try {
        cycleStack.push(delegateKey);
        resolvedDelegate = lookUpBindings(delegateKey);
      } finally {
        cycleStack.pop();
      }
      if (resolvedDelegate.contributionBindings().isEmpty()) {
        // This is guaranteed to result in a missing binding error, so it doesn't matter if the
        // binding is a Provision or Production, except if it is a @IntoMap method, in which
        // case the key will be of type Map<K, Provider<V>>, which will be "upgraded" into a
        // Map<K, Producer<V>> if it's requested in a ProductionComponent. This may result in a
        // strange error, that the RHS needs to be provided with an @Inject or @Provides
        // annotated method, but a user should be able to figure out if a @Produces annotation
        // is needed.
        // TODO(gak): revisit how we model missing delegates if/when we clean up how we model
        // binding declarations
        return bindingFactory.unresolvedDelegateBinding(delegateDeclaration);
      }
      // It doesn't matter which of these is selected, since they will later on produce a
      // duplicate binding error.
      ContributionBinding explicitDelegate =
          resolvedDelegate.contributionBindings().iterator().next();
      return bindingFactory.delegateBinding(delegateDeclaration, explicitDelegate);
    }

    /**
     * Returns the component that should contain the framework field for {@code binding}.
     *
     * <p>If {@code binding} is either not bound in an ancestor component or depends transitively on
     * bindings in this component, returns this component.
     *
     * <p>Otherwise, resolves {@code request} in this component's parent in order to resolve any
     * multibinding contributions in the parent, and returns the parent-resolved {@link
     * ResolvedBindings#owningComponent(ContributionBinding)}.
     */
    private TypeElement getOwningComponent(Key requestKey, ContributionBinding binding) {
      if (isResolvedInParent(requestKey, binding)
          && !new LocalDependencyChecker().dependsOnLocalBindings(binding)) {
        ResolvedBindings parentResolvedBindings =
            parentResolver.orElseThrow().resolvedContributionBindings.get(requestKey);
        return parentResolvedBindings.owningComponent(binding);
      } else {
        return componentDescriptor.typeElement();
      }
    }

    /**
     * Returns {@code true} if {@code binding} is owned by an ancestor. If so, {@linkplain #resolve
     * resolves} the {@link Key} in this component's parent. Don't resolve directly in the owning
     * component in case it depends on multibindings in any of its descendants.
     */
    private boolean isResolvedInParent(Key requestKey, ContributionBinding binding) {
      Optional<Resolver> owningResolver = getOwningResolver(binding);
      if (owningResolver.isPresent() && !owningResolver.orElseThrow().equals(this)) {
        parentResolver.orElseThrow().resolve(requestKey);
        return true;
      } else {
        return false;
      }
    }

    private Optional<Resolver> getOwningResolver(ContributionBinding binding) {

      List<Resolver> resolverLineage = new ArrayList<>(getResolverLineage());
      Collections.reverse(resolverLineage);
      if (binding.scope().isPresent() && binding.scope().orElseThrow().isReusable()) {
        for (Resolver requestResolver : resolverLineage) {
          // If a @Reusable binding was resolved in an ancestor, use that component.
          ResolvedBindings resolvedBindings =
              requestResolver.resolvedContributionBindings.get(binding.key());
          if (resolvedBindings != null
              && resolvedBindings.contributionBindings().contains(binding)) {
            return Optional.of(requestResolver);
          }
        }
        // If a @Reusable binding was not resolved in any ancestor, resolve it here.
        return Optional.empty();
      }

      for (Resolver requestResolver : resolverLineage) {
        if (requestResolver.containsExplicitBinding(binding)) {
          return Optional.of(requestResolver);
        }
      }

      // look for scope separately.  we do this for the case where @Singleton can appear twice
      // in the † compatibility mode
      Optional<Scope> bindingScope = binding.scope();
      if (bindingScope.isPresent()) {
        for (Resolver requestResolver : resolverLineage) {
          if (requestResolver.componentDescriptor.scopes().contains(bindingScope.orElseThrow())) {
            return Optional.of(requestResolver);
          }
        }
      }
      return Optional.empty();
    }

    private boolean containsExplicitBinding(ContributionBinding binding) {
      return explicitBindingsSet.contains(binding)
          || resolverContainsDelegateDeclarationForBinding(binding)
          || subcomponentDeclarations.containsKey(binding.key());
    }

    /** Returns true if {@code binding} was installed in a module in this resolver's component. */
    private boolean resolverContainsDelegateDeclarationForBinding(ContributionBinding binding) {
      if (!binding.kind().equals(DELEGATE)) {
        return false;
      }

      // Map multibinding key values are wrapped with a framework type. This needs to be undone
      // to look it up in the delegate declarations map.
      // TODO(erichang): See if we can standardize the way map keys are used in these data
      // structures, either always wrapped or unwrapped to be consistent and less errorprone.
      Key bindingKey = binding.key();
      if (compilerOptions.strictMultibindingValidation()
          && binding.contributionType().equals(ContributionType.MAP)) {
        bindingKey = keyFactory.unwrapMapValueType(bindingKey);
      }

      return delegateDeclarations.getOrDefault(bindingKey, Set.of()).stream()
          .anyMatch(
              declaration ->
                  declaration.contributingModule().equals(binding.contributingModule())
                      && declaration.bindingElement().equals(binding.bindingElement()));
    }

    /** Returns the resolver lineage from parent to child. */
    private List<Resolver> getResolverLineage() {
      List<Resolver> resolverList = new ArrayList<>();
      for (Optional<Resolver> currentResolver = Optional.of(this);
           currentResolver.isPresent();
           currentResolver = currentResolver.orElseThrow().parentResolver) {
        resolverList.add(currentResolver.orElseThrow());
      }
      Collections.reverse(resolverList);
      return resolverList;
    }

    /**
     * Returns the explicit {@link ContributionBinding}s that match the {@code key} from this
     * resolver.
     */
    private Set<ContributionBinding> getLocalExplicitBindings(Key key) {
      return Util.union(
          explicitBindings.getOrDefault(key, Set.of()),
          // @Binds @IntoMap declarations have key Map<K, V>, unlike @Provides @IntoMap or @Produces
          // @IntoMap, which have Map<K, Provider/Producer<V>> keys. So unwrap the key's type's
          // value type if it's a Map<K, Provider/Producer<V>> before looking in
          // delegateDeclarations. createDelegateBindings() will create bindings with the properly
          // wrapped key type.
          createDelegateBindings(delegateDeclarations.getOrDefault(keyFactory.unwrapMapValueType(key), Set.of())));
    }

    /**
     * Returns the explicit multibinding contributions that contribute to the map or set requested
     * by {@code key} from this resolver.
     */
    private Set<ContributionBinding> getLocalExplicitMultibindings(Key key) {
      Set<ContributionBinding> multibindings = new LinkedHashSet<>();
      return multibindings;
    }

    /**
     * Returns the {@link OptionalBindingDeclaration}s that match the {@code key} from this and all
     * ancestor resolvers.
     */
    private Set<OptionalBindingDeclaration> getOptionalBindingDeclarations(Key key) {
      Optional<Key> unwrapped = keyFactory.unwrapOptional(key);
      if (unwrapped.isEmpty()) {
        return Set.of();
      }
      Set<OptionalBindingDeclaration> declarations = new LinkedHashSet<>();
      for (Resolver resolver : getResolverLineage()) {
        declarations.addAll(resolver.optionalBindingDeclarations.getOrDefault(unwrapped.orElseThrow(), Set.of()));
      }
      return declarations;
    }

    /**
     * Returns the {@link ResolvedBindings} for {@code key} that was resolved in this resolver or an
     * ancestor resolver. Only checks for {@link ContributionBinding}s as {@link
     * MembersInjectionBinding}s are not inherited.
     */
    private Optional<ResolvedBindings> getPreviouslyResolvedBindings(Key key) {
      Optional<ResolvedBindings> result =
          Optional.ofNullable(resolvedContributionBindings.get(key));
      if (result.isPresent()) {
        return result;
      } else if (parentResolver.isPresent()) {
        return parentResolver.orElseThrow().getPreviouslyResolvedBindings(key);
      } else {
        return Optional.empty();
      }
    }

    private void resolveMembersInjection(Key key) {
      ResolvedBindings bindings = lookUpMembersInjectionBinding(key);
      resolveDependencies(bindings);
      resolvedMembersInjectionBindings.put(key, bindings);
    }

    void resolve(Key key) {
      // If we find a cycle, stop resolving. The original request will add it with all of the
      // other resolved deps.
      if (cycleStack.contains(key)) {
        return;
      }

      // If the binding was previously resolved in this (sub)component, don't resolve it again.
      if (resolvedContributionBindings.containsKey(key)) {
        return;
      }

      /*
       * If the binding was previously resolved in an ancestor component, then we may be able to
       * avoid resolving it here and just depend on the ancestor component resolution.
       *
       * 1. If it depends transitively on multibinding contributions or optional bindings with
       *    bindings from this subcomponent, then we have to resolve it in this subcomponent so
       *    that it sees the local bindings.
       *
       * 2. If there are any explicit bindings in this component, they may conflict with those in
       *    the ancestor component, so resolve them here so that conflicts can be caught.
       */
      if (getPreviouslyResolvedBindings(key).isPresent() && !Keys.isComponentOrCreator(key)) {
        /* Resolve in the parent in case there are multibinding contributions or conflicts in some
         * component between this one and the previously-resolved one. */
        parentResolver.orElseThrow().resolve(key);
        if (!new LocalDependencyChecker().dependsOnLocalBindings(key)
            && getLocalExplicitBindings(key).isEmpty()) {
          /* Cache the inherited parent component's bindings in case resolving at the parent found
           * bindings in some component between this one and the previously-resolved one. */
          resolvedContributionBindings.put(key, getPreviouslyResolvedBindings(key).orElseThrow());
          return;
        }
      }

      cycleStack.push(key);
      try {
        ResolvedBindings bindings = lookUpBindings(key);
        resolvedContributionBindings.put(key, bindings);
        resolveDependencies(bindings);
      } finally {
        cycleStack.pop();
      }
    }

    /**
     * {@link #resolve(Key) Resolves} each of the dependencies of the bindings owned by this
     * component.
     */
    private void resolveDependencies(ResolvedBindings resolvedBindings) {
      for (Binding binding : resolvedBindings.bindingsOwnedBy(componentDescriptor)) {
        for (DependencyRequest dependency : binding.dependencies()) {
          resolve(dependency.key());
        }
      }
    }

    /**
     * Returns all of the {@link ResolvedBindings} for {@link ContributionBinding}s from this and
     * all ancestor resolvers, indexed by {@link ResolvedBindings#key()}.
     */
    Map<Key, ResolvedBindings> getResolvedContributionBindings() {
      Map<Key, ResolvedBindings> bindings = new LinkedHashMap<>();
      parentResolver.ifPresent(parent -> bindings.putAll(parent.getResolvedContributionBindings()));
      bindings.putAll(resolvedContributionBindings);
      return bindings;
    }

    /**
     * Returns all of the {@link ResolvedBindings} for {@link MembersInjectionBinding} from this
     * resolvers, indexed by {@link ResolvedBindings#key()}.
     */
    Map<Key, ResolvedBindings> getResolvedMembersInjectionBindings() {
      return resolvedMembersInjectionBindings;
    }

    private final class LocalDependencyChecker {
      private final Set<Object> cycleChecker = new HashSet<>();

      /**
       * Returns {@code true} if any of the bindings resolved for {@code key} are multibindings with
       * contributions declared within this component's modules or optional bindings with present
       * values declared within this component's modules, or if any of its unscoped dependencies
       * depend on such bindings.
       *
       * <p>We don't care about scoped dependencies because they will never depend on bindings from
       * subcomponents.
       *
       * @throws IllegalArgumentException if {@link #getPreviouslyResolvedBindings(Key)} is empty
       */
      private boolean dependsOnLocalBindings(Key key) {
        // Don't recur infinitely if there are valid cycles in the dependency graph.
        // http://b/23032377
        if (!cycleChecker.add(key)) {
          return false;
        }
        return reentrantComputeIfAbsent(
            keyDependsOnLocalBindingsCache, key, this::dependsOnLocalBindingsUncached);
      }

      /**
       * Returns {@code true} if {@code binding} is unscoped (or has {@code @Reusable}
       * scope) and depends on multibindings with contributions declared within this component's
       * modules, or if any of its unscoped or {@code @Reusable} scoped dependencies depend
       * on such local multibindings.
       *
       * <p>We don't care about non-reusable scoped dependencies because they will never depend on
       * multibindings with contributions from subcomponents.
       */
      private boolean dependsOnLocalBindings(Binding binding) {
        if (!cycleChecker.add(binding)) {
          return false;
        }
        return reentrantComputeIfAbsent(
            bindingDependsOnLocalBindingsCache, binding, this::dependsOnLocalBindingsUncached);
      }

      private boolean dependsOnLocalBindingsUncached(Key key) {
        Preconditions.checkArgument(
            getPreviouslyResolvedBindings(key).isPresent(),
            "no previously resolved bindings in %s for %s",
            Resolver.this,
            key);
        ResolvedBindings previouslyResolvedBindings = getPreviouslyResolvedBindings(key).orElseThrow();
        if (hasLocalOptionalBindingContribution(previouslyResolvedBindings)) {
          return true;
        }

        for (Binding binding : previouslyResolvedBindings.bindings()) {
          if (dependsOnLocalBindings(binding)) {
            return true;
          }
        }
        return false;
      }

      private boolean dependsOnLocalBindingsUncached(Binding binding) {
        if (binding.scope().isEmpty() || binding.scope().orElseThrow().isReusable()) {
          for (DependencyRequest dependency : binding.dependencies()) {
            if (dependsOnLocalBindings(dependency.key())) {
              return true;
            }
          }
        }
        return false;
      }

      /**
       * Returns {@code true} if there is a contribution in this component for an {@code
       * Optional<Foo>} key that has not been contributed in a parent.
       */
      private boolean hasLocalOptionalBindingContribution(ResolvedBindings resolvedBindings) {
        if (resolvedBindings
            .contributionBindings()
            .stream()
            .map(ContributionBinding::kind)
            .anyMatch(isEqual(OPTIONAL))) {
          return !getLocalExplicitBindings(keyFactory.unwrapOptional(resolvedBindings.key()).orElseThrow())
              .isEmpty();
        } else {
          // If a parent contributes a @Provides Optional<Foo> binding and a child has a
          // @BindsOptionalOf Foo method, the two should conflict, even if there is no binding for
          // Foo on its own
          return !getOptionalBindingDeclarations(resolvedBindings.key()).isEmpty();
        }
      }
    }
  }
}
