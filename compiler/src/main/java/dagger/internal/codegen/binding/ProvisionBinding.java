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

import static dagger.spi.model.BindingKind.COMPONENT_PROVISION;
import static dagger.spi.model.BindingKind.PROVISION;
import static java.util.Objects.requireNonNull;

import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.spi.model.BindingKind;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Scope;
import dagger.spi.model.Key;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** A value object representing the mechanism by which a {@link Key} can be provided. */
public final class ProvisionBinding extends ContributionBinding {

  private final Key key;
  private final Optional<Element> bindingElement;
  private final Optional<TypeElement> contributingModule;
  private final BindingKind kind;
  private final Set<DependencyRequest> provisionDependencies;
  private final Optional<ProvisionBinding> unresolved;
  private final Optional<Scope> scope;

  private final IntSupplier hash = Suppliers.memoizeInt(() ->
      Objects.hash(key(), bindingElement(),
          contributingModule(), kind(),
          provisionDependencies(),
          unresolved(), scope()));

  private final Supplier<Boolean> requiresModuleInstance = Suppliers.memoize(super::requiresModuleInstance);

  ProvisionBinding(
      Key key,
      Optional<Element> bindingElement,
      Optional<TypeElement> contributingModule,
      BindingKind kind,
      Set<DependencyRequest> provisionDependencies,
      Optional<ProvisionBinding> unresolved,
      Optional<Scope> scope) {
    this.key = requireNonNull(key);
    this.bindingElement = requireNonNull(bindingElement);
    this.contributingModule = requireNonNull(contributingModule);
    this.kind = requireNonNull(kind);
    this.provisionDependencies = requireNonNull(provisionDependencies);
    this.unresolved = requireNonNull(unresolved);
    this.scope = requireNonNull(scope);
  }

  @Override
  public Set<DependencyRequest> explicitDependencies() {
    return provisionDependencies();
  }

  @Override
  public Key key() {
    return key;
  }

  @Override
  public Optional<Element> bindingElement() {
    return bindingElement;
  }

  @Override
  public Optional<TypeElement> contributingModule() {
    return contributingModule;
  }

  @Override
  public BindingKind kind() {
    return kind;
  }

  /**
   * Dependencies necessary to invoke an {@code @Inject} constructor or {@code @Provides} method.
   */
  public Set<DependencyRequest> provisionDependencies() {
    return provisionDependencies;
  }

  @Override
  public BindingType bindingType() {
    return BindingType.PROVISION;
  }

  @Override
  public Optional<ProvisionBinding> unresolved() {
    return unresolved;
  }

  // TODO(ronshapiro): we should be able to remove this, but AutoValue barks on the Builder's scope
  // method, saying that the method doesn't correspond to a property of ProvisionBinding
  @Override
  public Optional<Scope> scope() {
    return scope;
  }

  public static Builder builder() {
    return new Builder()
        .provisionDependencies(Set.of());
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  private static final Set<BindingKind> KINDS_TO_CHECK_FOR_NULL =
      new LinkedHashSet<>(List.of(PROVISION, COMPONENT_PROVISION));

  public boolean shouldCheckForNull(CompilerOptions compilerOptions) {
    return KINDS_TO_CHECK_FOR_NULL.contains(kind())
        && contributedPrimitiveType().isEmpty()
        && compilerOptions.doCheckForNulls();
  }

  // Profiling determined that this method is called enough times that memoizing it had a measurable
  // performance improvement for large components.
  @Override
  public boolean requiresModuleInstance() {
    return requiresModuleInstance.get();
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProvisionBinding that = (ProvisionBinding) o;
    return hashCode() == that.hashCode()
        && key.equals(that.key)
        && bindingElement.equals(that.bindingElement)
        && contributingModule.equals(that.contributingModule)
        && kind == that.kind
        && provisionDependencies.equals(that.provisionDependencies)
        && unresolved.equals(that.unresolved)
        && scope.equals(that.scope);
  }

  @Override
  public int hashCode() {
    return hash.getAsInt();
  }

  /** A {@link ProvisionBinding} builder. */
  static final class Builder {
    private Key key;
    private Optional<Element> bindingElement = Optional.empty();
    private Optional<TypeElement> contributingModule = Optional.empty();
    private BindingKind kind;
    private Set<DependencyRequest> provisionDependencies;
    private Optional<ProvisionBinding> unresolved = Optional.empty();
    private Optional<Scope> scope = Optional.empty();

    private Builder() {
    }

    private Builder(ProvisionBinding source) {
      this.key = source.key();
      this.bindingElement = source.bindingElement();
      this.contributingModule = source.contributingModule();
      this.kind = source.kind();
      this.provisionDependencies = source.provisionDependencies();
      this.unresolved = source.unresolved();
      this.scope = source.scope();
    }

    Builder dependencies(Set<DependencyRequest> dependencies) {
      return provisionDependencies(dependencies);
    }

    Builder dependencies(DependencyRequest... dependencies) {
      return dependencies(new LinkedHashSet<>(List.of(dependencies)));
    }

    Builder clearBindingElement() {
      return bindingElement(Optional.empty());
    }

    Builder key(Key key) {
      this.key = key;
      return this;
    }

    Builder bindingElement(Element bindingElement) {
      this.bindingElement = Optional.of(bindingElement);
      return this;
    }

    Builder bindingElement(Optional<Element> bindingElement) {
      this.bindingElement = bindingElement;
      return this;
    }

    Builder contributingModule(TypeElement contributingModule) {
      this.contributingModule = Optional.of(contributingModule);
      return this;
    }

    Builder kind(BindingKind kind) {
      this.kind = kind;
      return this;
    }

    Builder provisionDependencies(Set<DependencyRequest> provisionDependencies) {
      this.provisionDependencies = provisionDependencies;
      return this;
    }

    Builder unresolved(ProvisionBinding unresolved) {
      this.unresolved = Optional.of(unresolved);
      return this;
    }

    Builder scope(Optional<Scope> scope) {
      this.scope = scope;
      return this;
    }

    ProvisionBinding build() {
      return new ProvisionBinding(
          this.key,
          this.bindingElement,
          this.contributingModule,
          this.kind,
          this.provisionDependencies,
          this.unresolved,
          this.scope);
    }
  }
}
