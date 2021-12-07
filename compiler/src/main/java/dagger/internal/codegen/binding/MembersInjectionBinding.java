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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import dagger.internal.codegen.base.Suppliers;
import dagger.model.BindingKind;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/** Represents the full members injection of a particular type. */
public final class MembersInjectionBinding extends Binding {
  private final Key key;
  private final ImmutableSet<DependencyRequest> explicitDependencies;
  private final TypeElement membersInjectedType;
  private final Optional<MembersInjectionBinding> unresolved;
  private final ImmutableSortedSet<MembersInjectionBinding.InjectionSite> injectionSites;

  private final IntSupplier hash = Suppliers.memoizeInt(() ->
      Objects.hash(key(),
          explicitDependencies(),
          membersInjectedType(),
          unresolved(),
          injectionSites()));

  MembersInjectionBinding(
      Key key,
      ImmutableSet<DependencyRequest> explicitDependencies,
      TypeElement membersInjectedType,
      Optional<MembersInjectionBinding> unresolved,
      ImmutableSortedSet<MembersInjectionBinding.InjectionSite> injectionSites) {
    this.key = requireNonNull(key);
    this.explicitDependencies = requireNonNull(explicitDependencies);
    this.membersInjectedType = requireNonNull(membersInjectedType);
    this.unresolved = requireNonNull(unresolved);
    this.injectionSites = requireNonNull(injectionSites);
  }

  @Override
  public Key key() {
    return key;
  }

  @Override
  public Optional<Element> bindingElement() {
    return Optional.of(membersInjectedType());
  }

  public TypeElement membersInjectedType() {
    return membersInjectedType;
  }

  @Override
  public Optional<MembersInjectionBinding> unresolved() {
    return unresolved;
  }

  @Override
  public Optional<TypeElement> contributingModule() {
    return Optional.empty();
  }

  /** The set of individual sites where {@code Inject} is applied. */
  public ImmutableSortedSet<MembersInjectionBinding.InjectionSite> injectionSites() {
    return injectionSites;
  }

  @Override
  public BindingType bindingType() {
    return BindingType.MEMBERS_INJECTION;
  }

  @Override
  public ImmutableSet<DependencyRequest> explicitDependencies() {
    return explicitDependencies;
  }

  @Override
  public BindingKind kind() {
    return BindingKind.MEMBERS_INJECTION;
  }

  @Override
  public boolean isNullable() {
    return false;
  }

  /**
   * Returns {@code true} if any of this binding's injection sites are directly on the bound type.
   */
  public boolean hasLocalInjectionSites() {
    return injectionSites()
        .stream()
        .anyMatch(
            injectionSite ->
                injectionSite.element().getEnclosingElement().equals(membersInjectedType()));
  }

  @Override
  public boolean requiresModuleInstance() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MembersInjectionBinding that = (MembersInjectionBinding) o;
    return key.equals(that.key)
        && explicitDependencies.equals(that.explicitDependencies)
        && membersInjectedType.equals(that.membersInjectedType)
        && unresolved.equals(that.unresolved)
        && injectionSites.equals(that.injectionSites);
  }

  // TODO(ronshapiro,dpb): simplify the equality semantics
  @Override
  public int hashCode() {
    return hash.getAsInt();
  }

  /** Metadata about a field or method injection site. */
  public static final class InjectionSite {
    private final MembersInjectionBinding.InjectionSite.Kind kind;
    private final Element element;
    private final ImmutableSet<DependencyRequest> dependencies;
    private final IntSupplier hash = Suppliers.memoizeInt(() -> Objects.hash(
        kind(),
        element(),
        dependencies(),
        indexAmongAtInjectMembersWithSameSimpleName()));

    InjectionSite(
        MembersInjectionBinding.InjectionSite.Kind kind,
        Element element,
        ImmutableSet<DependencyRequest> dependencies) {
      this.kind = kind;
      this.element = element;
      this.dependencies = dependencies;
    }

    private final IntSupplier indexAmongAtInjectMembersWithSameSimpleName = Suppliers.memoizeInt(() -> element()
        .getEnclosingElement()
        .getEnclosedElements()
        .stream()
        .filter(element -> isAnnotationPresent(element, Inject.class))
        .filter(element -> !element.getModifiers().contains(Modifier.PRIVATE))
        .filter(element -> element.getSimpleName().equals(this.element().getSimpleName()))
        .collect(toList())
        .indexOf(element()));

    /** The type of injection site. */
    public enum Kind {
      FIELD,
      METHOD,
    }

    public MembersInjectionBinding.InjectionSite.Kind kind() {
      return kind;
    }

    public Element element() {
      return element;
    }

    public ImmutableSet<DependencyRequest> dependencies() {
      return dependencies;
    }

    /**
     * Returns the index of {@link #element()} in its parents {@code @Inject} members that have the
     * same simple name. This method filters out private elements so that the results will be
     * consistent independent of whether the build system uses header jars or not.
     */
    public int indexAmongAtInjectMembersWithSameSimpleName() {
      return indexAmongAtInjectMembersWithSameSimpleName.getAsInt();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      InjectionSite that = (InjectionSite) o;
      return hashCode() == that.hashCode()
          && indexAmongAtInjectMembersWithSameSimpleName() == that.indexAmongAtInjectMembersWithSameSimpleName()
          && kind == that.kind
          && element.equals(that.element)
          && dependencies.equals(that.dependencies);
    }

    @Override
    public int hashCode() {
      return hash.getAsInt();
    }

    public static InjectionSite field(VariableElement element, DependencyRequest dependency) {
      return new InjectionSite(
          Kind.FIELD, element, ImmutableSet.of(dependency));
    }

    public static InjectionSite method(
        ExecutableElement element, Iterable<DependencyRequest> dependencies) {
      return new InjectionSite(
          Kind.METHOD, element, ImmutableSet.copyOf(dependencies));
    }
  }
}
