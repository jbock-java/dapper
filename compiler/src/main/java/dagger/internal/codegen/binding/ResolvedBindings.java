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

import static dagger.internal.codegen.base.Preconditions.checkArgument;
import static dagger.internal.codegen.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;

import dagger.internal.codegen.collect.ImmutableCollection;
import dagger.internal.codegen.collect.ImmutableMap;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.collect.ImmutableSetMultimap;
import dagger.internal.codegen.collect.Multimap;
import dagger.spi.model.Key;
import io.jbock.auto.value.AutoValue;
import io.jbock.auto.value.extension.memoized.Memoized;
import javax.lang.model.element.TypeElement;

/**
 * The collection of bindings that have been resolved for a key. For valid graphs, contains exactly
 * one binding.
 *
 * <p>Separate {@code ResolvedBindings} instances should be used if a {@code
 * MembersInjectionBinding} and a {@code ProvisionBinding} for the same key exist in the same
 * component. (This will only happen if a type has an {@code @Inject} constructor and members, the
 * component has a members injection method, and the type is also requested normally.)
 */
@AutoValue
abstract class ResolvedBindings {
  /** The binding key for which the {@code #bindings()} have been resolved. */
  abstract Key key();

  /**
   * The {@code ContributionBinding}s for {@code #key()} indexed by the component that owns the
   * binding. Each key in the multimap is a part of the same component ancestry.
   */
  abstract ImmutableSetMultimap<TypeElement, ContributionBinding> allContributionBindings();

  /**
   * The {@code MembersInjectionBinding}s for {@code #key()} indexed by the component that owns the
   * binding. Each key in the map is a part of the same component ancestry.
   */
  abstract ImmutableMap<TypeElement, MembersInjectionBinding> allMembersInjectionBindings();

  /** The multibinding declarations for {@code #key()}. */
  abstract ImmutableSet<MultibindingDeclaration> multibindingDeclarations();

  /** The subcomponent declarations for {@code #key()}. */
  abstract ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations();

  // Computing the hash code is an expensive operation.
  @Memoized
  @Override
  public abstract int hashCode();

  // Suppresses ErrorProne warning that hashCode was overridden w/o equals
  @Override
  public abstract boolean equals(Object other);

  /** All bindings for {@code #key()}, indexed by the component that owns the binding. */
  final ImmutableSetMultimap<TypeElement, ? extends Binding> allBindings() {
    return !allMembersInjectionBindings().isEmpty()
        ? allMembersInjectionBindings().asMultimap()
        : allContributionBindings();
  }

  /** All bindings for {@code #key()}, regardless of which component owns them. */
  final ImmutableCollection<? extends Binding> bindings() {
    return allBindings().values();
  }

  /**
   * {@code true} if there are no {@code #bindings()}, {@code #multibindingDeclarations()}, {@code
   * #optionalBindingDeclarations()}, or {@code #subcomponentDeclarations()}.
   */
  final boolean isEmpty() {
    return allMembersInjectionBindings().isEmpty()
        && allContributionBindings().isEmpty()
        && multibindingDeclarations().isEmpty()
        && subcomponentDeclarations().isEmpty();
  }

  /** All bindings for {@code #key()} that are owned by a component. */
  ImmutableSet<? extends Binding> bindingsOwnedBy(ComponentDescriptor component) {
    return allBindings().get(toJavac(component.typeElement()));
  }

  /**
   * All contribution bindings, regardless of owning component. Empty if this is a members-injection
   * binding.
   */
  @Memoized
  ImmutableSet<ContributionBinding> contributionBindings() {
    // TODO(ronshapiro): consider optimizing ImmutableSet.copyOf(Collection) for small immutable
    // collections so that it doesn't need to call toArray(). Even though this method is memoized,
    // toArray() can take ~150ms for large components, and there are surely other places in the
    // processor that can benefit from this.
    return ImmutableSet.copyOf(allContributionBindings().values());
  }

  /** The component that owns {@code binding}. */
  final TypeElement owningComponent(ContributionBinding binding) {
    checkArgument(
        contributionBindings().contains(binding),
        "binding is not resolved for %s: %s",
        key(),
        binding);
    return getOnlyElement(allContributionBindings().inverse().get(binding));
  }

  /** Creates a {@code ResolvedBindings} for contribution bindings. */
  static ResolvedBindings forContributionBindings(
      Key key,
      Multimap<TypeElement, ContributionBinding> contributionBindings,
      Iterable<MultibindingDeclaration> multibindings,
      Iterable<SubcomponentDeclaration> subcomponentDeclarations) {
    return new AutoValue_ResolvedBindings(
        key,
        ImmutableSetMultimap.copyOf(contributionBindings),
        ImmutableMap.of(),
        ImmutableSet.copyOf(multibindings),
        ImmutableSet.copyOf(subcomponentDeclarations));
  }

  /** Creates a {@code ResolvedBindings} for members injection bindings. */
  static ResolvedBindings forMembersInjectionBinding(
      Key key,
      ComponentDescriptor owningComponent,
      MembersInjectionBinding ownedMembersInjectionBinding) {
    return new AutoValue_ResolvedBindings(
        key,
        ImmutableSetMultimap.of(),
        ImmutableMap.of(toJavac(owningComponent.typeElement()), ownedMembersInjectionBinding),
        ImmutableSet.of(),
        ImmutableSet.of());
  }

  /** Creates a {@code ResolvedBindings} appropriate for when there are no bindings for the key. */
  static ResolvedBindings noBindings(Key key) {
    return new AutoValue_ResolvedBindings(
        key, ImmutableSetMultimap.of(), ImmutableMap.of(), ImmutableSet.of(), ImmutableSet.of());
  }
}
