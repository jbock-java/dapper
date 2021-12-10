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

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerTypes.isFutureType;
import static java.util.Objects.requireNonNull;

import com.google.auto.common.Equivalence;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.base.Suppliers;
import dagger.model.BindingKind;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/** A value object representing the mechanism by which a {@link Key} can be produced. */
public final class ProductionBinding extends ContributionBinding {

  private final Supplier<Boolean> requiresModuleInstance = Suppliers.memoize(super::requiresModuleInstance);

  private final ContributionType contributionType;
  private final Key key;
  private final Optional<Element> bindingElement;
  private final Optional<TypeElement> contributingModule;
  private final BindingKind kind;
  private final ImmutableSet<DependencyRequest> explicitDependencies;
  private final Optional<DeclaredType> nullableType;
  private final Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedMapKeyAnnotation;
  private final Optional<ProductionBinding> unresolved;
  private final Optional<ProductionBinding.ProductionKind> productionKind;
  private final ImmutableList<? extends TypeMirror> thrownTypes;
  private final Optional<DependencyRequest> executorRequest;
  private final Optional<DependencyRequest> monitorRequest;

  private final IntSupplier hash = Suppliers.memoizeInt(() -> Objects.hash(contributionType(),
      key(), bindingElement(), contributingModule(), kind(), explicitDependencies(),
      nullableType(), wrappedMapKeyAnnotation(), unresolved(), productionKind(),
      thrownTypes(), executorRequest(), monitorRequest()));

  ProductionBinding(
      ContributionType contributionType,
      Key key,
      Optional<Element> bindingElement,
      Optional<TypeElement> contributingModule,
      BindingKind kind,
      ImmutableSet<DependencyRequest> explicitDependencies,
      Optional<DeclaredType> nullableType,
      Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedMapKeyAnnotation,
      Optional<ProductionBinding> unresolved,
      Optional<ProductionBinding.ProductionKind> productionKind,
      ImmutableList<? extends TypeMirror> thrownTypes,
      Optional<DependencyRequest> executorRequest,
      Optional<DependencyRequest> monitorRequest) {
    this.contributionType = requireNonNull(contributionType);
    this.key = requireNonNull(key);
    this.bindingElement = requireNonNull(bindingElement);
    this.contributingModule = requireNonNull(contributingModule);
    this.kind = requireNonNull(kind);
    this.explicitDependencies = requireNonNull(explicitDependencies);
    this.nullableType = requireNonNull(nullableType);
    this.wrappedMapKeyAnnotation = requireNonNull(wrappedMapKeyAnnotation);
    this.unresolved = requireNonNull(unresolved);
    this.productionKind = requireNonNull(productionKind);
    this.thrownTypes = requireNonNull(thrownTypes);
    this.executorRequest = requireNonNull(executorRequest);
    this.monitorRequest = requireNonNull(monitorRequest);
  }

  @Override
  public ContributionType contributionType() {
    return contributionType;
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

  @Override
  public ImmutableSet<DependencyRequest> explicitDependencies() {
    return explicitDependencies;
  }

  @Override
  public Optional<DeclaredType> nullableType() {
    return nullableType;
  }

  @Override
  public Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedMapKeyAnnotation() {
    return wrappedMapKeyAnnotation;
  }

  @Override
  public BindingType bindingType() {
    return BindingType.PRODUCTION;
  }

  @Override
  public Optional<ProductionBinding> unresolved() {
    return unresolved;
  }

  @Override
  public ImmutableSet<DependencyRequest> implicitDependencies() {
    return Stream.of(executorRequest(), monitorRequest())
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableSet());
  }

  /** What kind of object a {@code @Produces}-annotated method returns. */
  public enum ProductionKind {
    /** A value. */
    IMMEDIATE,
    /** A {@code ListenableFuture<T>}. */
    FUTURE,
    /** A {@code Set<ListenableFuture<T>>}. */
    SET_OF_FUTURE;

    /** Returns the kind of object a {@code @Produces}-annotated method returns. */
    public static ProductionKind fromProducesMethod(ExecutableElement producesMethod) {
      if (isFutureType(producesMethod.getReturnType())) {
        return FUTURE;
      } else if (ContributionType.fromBindingElement(producesMethod)
          .equals(ContributionType.SET_VALUES)
          && isFutureType(SetType.from(producesMethod.getReturnType()).elementType())) {
        return SET_OF_FUTURE;
      } else {
        return IMMEDIATE;
      }
    }
  }

  /**
   * Returns the kind of object the produces method returns. All production bindings from
   * {@code @Produces} methods will have a production kind, but synthetic production bindings may
   * not.
   */
  public Optional<ProductionBinding.ProductionKind> productionKind() {
    return productionKind;
  }

  /** Returns the list of types in the throws clause of the method. */
  public ImmutableList<? extends TypeMirror> thrownTypes() {
    return thrownTypes;
  }

  /**
   * If this production requires an executor, this will be the corresponding request.  All
   * production bindings from {@code @Produces} methods will have an executor request, but
   * synthetic production bindings may not.
   */
  Optional<DependencyRequest> executorRequest() {
    return executorRequest;
  }

  /** If this production requires a monitor, this will be the corresponding request.  All
   * production bindings from {@code @Produces} methods will have a monitor request, but synthetic
   * production bindings may not.
   */
  Optional<DependencyRequest> monitorRequest() {
    return monitorRequest;
  }

  // Profiling determined that this method is called enough times that memoizing it had a measurable
  // performance improvement for large components.
  @Override
  public boolean requiresModuleInstance() {
    return requiresModuleInstance.get();
  }

  public static Builder builder() {
    return new Builder()
        .explicitDependencies(ImmutableList.of())
        .thrownTypes(ImmutableList.of());
  }

  @Override
  public Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProductionBinding that = (ProductionBinding) o;
    return hashCode() == that.hashCode()
        && contributionType == that.contributionType
        && key.equals(that.key)
        && bindingElement.equals(that.bindingElement)
        && contributingModule.equals(that.contributingModule)
        && kind == that.kind
        && explicitDependencies.equals(that.explicitDependencies)
        && nullableType.equals(that.nullableType)
        && wrappedMapKeyAnnotation.equals(that.wrappedMapKeyAnnotation)
        && unresolved.equals(that.unresolved)
        && productionKind.equals(that.productionKind)
        && thrownTypes.equals(that.thrownTypes)
        && executorRequest.equals(that.executorRequest)
        && monitorRequest.equals(that.monitorRequest);
  }

  @Override
  public int hashCode() {
    return hash.getAsInt();
  }

  /** A {@link ProductionBinding} builder. */
  static class Builder extends ContributionBinding.Builder<ProductionBinding, Builder> {
    private ContributionType contributionType;
    private Key key;
    private Optional<Element> bindingElement = Optional.empty();
    private Optional<TypeElement> contributingModule = Optional.empty();
    private BindingKind kind;
    private ImmutableSet<DependencyRequest> explicitDependencies;
    private Optional<DeclaredType> nullableType = Optional.empty();
    private Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedMapKeyAnnotation = Optional.empty();
    private Optional<ProductionBinding> unresolved = Optional.empty();
    private Optional<ProductionBinding.ProductionKind> productionKind = Optional.empty();
    private ImmutableList<? extends TypeMirror> thrownTypes;
    private Optional<DependencyRequest> executorRequest = Optional.empty();
    private Optional<DependencyRequest> monitorRequest = Optional.empty();

    Builder() {
    }

    private Builder(ProductionBinding source) {
      this.contributionType = source.contributionType();
      this.key = source.key();
      this.bindingElement = source.bindingElement();
      this.contributingModule = source.contributingModule();
      this.kind = source.kind();
      this.explicitDependencies = source.explicitDependencies();
      this.nullableType = source.nullableType();
      this.wrappedMapKeyAnnotation = source.wrappedMapKeyAnnotation();
      this.unresolved = source.unresolved();
      this.productionKind = source.productionKind();
      this.thrownTypes = source.thrownTypes();
      this.executorRequest = source.executorRequest();
      this.monitorRequest = source.monitorRequest();
    }

    @Override
    public Builder dependencies(Iterable<DependencyRequest> dependencies) {
      return explicitDependencies(dependencies);
    }

    @Override
    public Builder contributionType(ContributionType contributionType) {
      this.contributionType = contributionType;
      return this;
    }

    @Override
    public Builder key(Key key) {
      this.key = key;
      return this;
    }

    @Override
    public Builder bindingElement(Element bindingElement) {
      this.bindingElement = Optional.of(bindingElement);
      return this;
    }

    @Override
    Builder bindingElement(Optional<Element> bindingElement) {
      this.bindingElement = bindingElement;
      return this;
    }

    @Override
    Builder contributingModule(TypeElement contributingModule) {
      this.contributingModule = Optional.of(contributingModule);
      return this;
    }

    @Override
    public Builder kind(BindingKind kind) {
      this.kind = kind;
      return this;
    }

    Builder explicitDependencies(Iterable<DependencyRequest> explicitDependencies) {
      this.explicitDependencies = ImmutableSet.copyOf(explicitDependencies);
      return this;
    }

    @Override
    public Builder nullableType(Optional<DeclaredType> nullableType) {
      this.nullableType = nullableType;
      return this;
    }

    @Override
    Builder wrappedMapKeyAnnotation(Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedMapKeyAnnotation) {
      this.wrappedMapKeyAnnotation = wrappedMapKeyAnnotation;
      return this;
    }

    @Override
    public Builder unresolved(ProductionBinding unresolved) {
      this.unresolved = Optional.of(unresolved);
      return this;
    }

    Builder productionKind(ProductionBinding.ProductionKind productionKind) {
      this.productionKind = Optional.of(productionKind);
      return this;
    }

    Builder thrownTypes(Iterable<? extends TypeMirror> thrownTypes) {
      this.thrownTypes = ImmutableList.copyOf(thrownTypes);
      return this;
    }

    Builder executorRequest(DependencyRequest executorRequest) {
      this.executorRequest = Optional.of(executorRequest);
      return this;
    }

    Builder monitorRequest(DependencyRequest monitorRequest) {
      this.monitorRequest = Optional.of(monitorRequest);
      return this;
    }

    @Override
    ProductionBinding autoBuild() {
      return new ProductionBinding(
          this.contributionType,
          this.key,
          this.bindingElement,
          this.contributingModule,
          this.kind,
          this.explicitDependencies,
          this.nullableType,
          this.wrappedMapKeyAnnotation,
          this.unresolved,
          this.productionKind,
          this.thrownTypes,
          this.executorRequest,
          this.monitorRequest);
    }
  }
}
