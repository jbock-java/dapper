/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static java.util.Objects.requireNonNull;

import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.extension.DaggerStreams;
import dagger.model.Key;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;

/**
 * The collection of bindings that have been resolved for a key. For valid graphs, contains exactly
 * one binding.
 *
 * <p>Separate {@link ResolvedBindings} instances should be used if a {@link
 * MembersInjectionBinding} and a {@link ProvisionBinding} for the same key exist in the same
 * component. (This will only happen if a type has an {@code @Inject} constructor and members, the
 * component has a members injection method, and the type is also requested normally.)
 */
final class ResolvedBindings {

  private final Supplier<Set<ContributionBinding>> contributionBindings = Suppliers.memoize(() ->
      allContributionBindings().values().stream()
          .flatMap(Set::stream)
          .collect(DaggerStreams.toImmutableSet()));

  private final IntSupplier hash = Suppliers.memoizeInt(() -> Objects.hash(key(),
      allContributionBindings(),
      allMembersInjectionBindings(),
      multibindingDeclarations(),
      subcomponentDeclarations(),
      optionalBindingDeclarations()));

  private final Key key;
  private final Map<TypeElement, Set<ContributionBinding>> allContributionBindings;
  private final Map<TypeElement, MembersInjectionBinding> allMembersInjectionBindings;
  private final Set<MultibindingDeclaration> multibindingDeclarations;
  private final Set<SubcomponentDeclaration> subcomponentDeclarations;
  private final Set<OptionalBindingDeclaration> optionalBindingDeclarations;

  ResolvedBindings(
      Key key,
      Map<TypeElement, Set<ContributionBinding>> allContributionBindings,
      Map<TypeElement, MembersInjectionBinding> allMembersInjectionBindings,
      Set<MultibindingDeclaration> multibindingDeclarations,
      Set<SubcomponentDeclaration> subcomponentDeclarations,
      Set<OptionalBindingDeclaration> optionalBindingDeclarations) {
    this.key = requireNonNull(key);
    this.allContributionBindings = requireNonNull(allContributionBindings);
    this.allMembersInjectionBindings = requireNonNull(allMembersInjectionBindings);
    this.multibindingDeclarations = requireNonNull(multibindingDeclarations);
    this.subcomponentDeclarations = requireNonNull(subcomponentDeclarations);
    this.optionalBindingDeclarations = requireNonNull(optionalBindingDeclarations);
  }


  /** The binding key for which the {@link #bindings()} have been resolved. */
  Key key() {
    return key;
  }

  /**
   * The {@link ContributionBinding}s for {@link #key()} indexed by the component that owns the
   * binding. Each key in the multimap is a part of the same component ancestry.
   */
  Map<TypeElement, Set<ContributionBinding>> allContributionBindings() {
    return allContributionBindings;
  }

  /**
   * The {@link MembersInjectionBinding}s for {@link #key()} indexed by the component that owns the
   * binding.  Each key in the map is a part of the same component ancestry.
   */
  Map<TypeElement, MembersInjectionBinding> allMembersInjectionBindings() {
    return allMembersInjectionBindings;
  }

  /** The multibinding declarations for {@link #key()}. */
  Set<MultibindingDeclaration> multibindingDeclarations() {
    return multibindingDeclarations;
  }

  /** The subcomponent declarations for {@link #key()}. */
  Set<SubcomponentDeclaration> subcomponentDeclarations() {
    return subcomponentDeclarations;
  }

  /** The optional binding declarations for {@link #key()}. */
  Set<OptionalBindingDeclaration> optionalBindingDeclarations() {
    return optionalBindingDeclarations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ResolvedBindings that = (ResolvedBindings) o;
    return key.equals(that.key)
        && allContributionBindings.equals(that.allContributionBindings)
        && allMembersInjectionBindings.equals(that.allMembersInjectionBindings)
        && multibindingDeclarations.equals(that.multibindingDeclarations)
        && subcomponentDeclarations.equals(that.subcomponentDeclarations)
        && optionalBindingDeclarations.equals(that.optionalBindingDeclarations);
  }

  @Override
  public int hashCode() {
    return hash.getAsInt();
  }

  /** All bindings for {@link #key()}, indexed by the component that owns the binding. */
  Map<TypeElement, Set<? extends Binding>> allBindings() {
    if (allMembersInjectionBindings().isEmpty()) {
      @SuppressWarnings(value = {"unchecked", "rawtypes"})
      Map<TypeElement, Set<? extends Binding>> contributionBindings = (Map) allContributionBindings();
      return contributionBindings;
    }
    return Util.transformValues(allMembersInjectionBindings(), Set::of);
  }

  /** All bindings for {@link #key()}, regardless of which component owns them. */
  Set<? extends Binding> bindings() {
    return allBindings().values().stream().flatMap(Set::stream).collect(Collectors.toSet());
  }

  /**
   * {@code true} if there are no {@link #bindings()}, {@link #multibindingDeclarations()}, {@link
   * #optionalBindingDeclarations()}, or {@link #subcomponentDeclarations()}.
   */
  boolean isEmpty() {
    return allMembersInjectionBindings().isEmpty()
        && allContributionBindings().isEmpty()
        && multibindingDeclarations().isEmpty()
        && optionalBindingDeclarations().isEmpty()
        && subcomponentDeclarations().isEmpty();
  }

  /** All bindings for {@link #key()} that are owned by a component. */
  Set<? extends Binding> bindingsOwnedBy(ComponentDescriptor component) {
    return allBindings().getOrDefault(component.typeElement(), Set.of());
  }

  /**
   * All contribution bindings, regardless of owning component. Empty if this is a members-injection
   * binding.
   */
  Set<ContributionBinding> contributionBindings() {
    // TODO(ronshapiro): consider optimizing ImmutableSet.copyOf(Collection) for small immutable
    // collections so that it doesn't need to call toArray(). Even though this method is memoized,
    // toArray() can take ~150ms for large components, and there are surely other places in the
    // processor that can benefit from this.
    return contributionBindings.get();
  }

  /** The component that owns {@code binding}. */
  TypeElement owningComponent(ContributionBinding binding) {
    for (Map.Entry<TypeElement, Set<ContributionBinding>> entry : allContributionBindings.entrySet()) {
      if (entry.getValue().contains(binding)) {
        return entry.getKey();
      }
    }
    throw new IllegalStateException(
        String.format("binding is not resolved for %s: %s",
            key(),
            binding));
  }

  /** Creates a {@link ResolvedBindings} for contribution bindings. */
  static ResolvedBindings forContributionBindings(
      Key key,
      Map<TypeElement, List<ContributionBinding>> contributionBindings,
      Set<MultibindingDeclaration> multibindings,
      Set<SubcomponentDeclaration> subcomponentDeclarations,
      Set<OptionalBindingDeclaration> optionalBindingDeclarations) {
    return new ResolvedBindings(
        key,
        Util.transformValues(contributionBindings, LinkedHashSet::new),
        Map.of(),
        multibindings,
        subcomponentDeclarations,
        optionalBindingDeclarations);
  }

  /**
   * Creates a {@link ResolvedBindings} for members injection bindings.
   */
  static ResolvedBindings forMembersInjectionBinding(
      Key key,
      ComponentDescriptor owningComponent,
      MembersInjectionBinding ownedMembersInjectionBinding) {
    return new ResolvedBindings(
        key,
        Map.of(),
        Map.of(owningComponent.typeElement(), ownedMembersInjectionBinding),
        Set.of(),
        Set.of(),
        Set.of());
  }

  /**
   * Creates a {@link ResolvedBindings} appropriate for when there are no bindings for the key.
   */
  static ResolvedBindings noBindings(Key key) {
    return new ResolvedBindings(
        key,
        Map.of(),
        Map.of(),
        Set.of(),
        Set.of(),
        Set.of());
  }
}
