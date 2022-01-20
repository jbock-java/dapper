/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.internal.codegen.writing;

import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.writing.BindingRepresentations.scope;
import static dagger.internal.codegen.writing.DelegateRequestRepresentation.isBindsScopeStrongerThanDependencyScope;
import static dagger.model.BindingKind.DELEGATE;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.model.RequestKind;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.TypeElement;

/**
 * A binding representation that returns expressions based on a framework instance expression. i.e.
 * provider.get()
 */
final class FrameworkInstanceBindingRepresentation implements BindingRepresentation {
  private final BindingGraph graph;
  private final boolean isFastInit;
  private final ProvisionBinding binding;
  private final ComponentImplementation componentImplementation;
  private final DelegateRequestRepresentation.Factory delegateRequestRepresentationFactory;
  private final DerivedFromFrameworkInstanceRequestRepresentation.Factory
      derivedFromFrameworkInstanceRequestRepresentationFactory;
  private final PrivateMethodRequestRepresentation.Factory
      privateMethodRequestRepresentationFactory;
  private final ProviderInstanceRequestRepresentation.Factory
      providerInstanceRequestRepresentationFactory;
  private final UnscopedDirectInstanceRequestRepresentationFactory
      unscopedDirectInstanceRequestRepresentationFactory;
  private final UnscopedFrameworkInstanceCreationExpressionFactory
      unscopedFrameworkInstanceCreationExpressionFactory;
  private final DirectInstanceBindingRepresentation directInstanceBindingRepresentation;
  private final SwitchingProviders switchingProviders;
  private final Map<BindingRequest, RequestRepresentation> requestRepresentations = new HashMap<>();
  private final FrameworkInstanceSupplier providerField;

  @AssistedInject
  FrameworkInstanceBindingRepresentation(
      @Assisted ProvisionBinding binding,
      @Assisted DirectInstanceBindingRepresentation directInstanceBindingRepresentation,
      SwitchingProviders switchingProviders,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      DelegateRequestRepresentation.Factory delegateRequestRepresentationFactory,
      DerivedFromFrameworkInstanceRequestRepresentation.Factory
          derivedFromFrameworkInstanceRequestRepresentationFactory,
      PrivateMethodRequestRepresentation.Factory privateMethodRequestRepresentationFactory,
      ProviderInstanceRequestRepresentation.Factory providerInstanceRequestRepresentationFactory,
      UnscopedDirectInstanceRequestRepresentationFactory
          unscopedDirectInstanceRequestRepresentationFactory,
      UnscopedFrameworkInstanceCreationExpressionFactory
          unscopedFrameworkInstanceCreationExpressionFactory,
      CompilerOptions compilerOptions) {
    this.binding = binding;
    this.switchingProviders = switchingProviders;
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.delegateRequestRepresentationFactory = delegateRequestRepresentationFactory;
    this.derivedFromFrameworkInstanceRequestRepresentationFactory =
        derivedFromFrameworkInstanceRequestRepresentationFactory;
    this.privateMethodRequestRepresentationFactory = privateMethodRequestRepresentationFactory;
    this.providerInstanceRequestRepresentationFactory =
        providerInstanceRequestRepresentationFactory;
    this.unscopedDirectInstanceRequestRepresentationFactory =
        unscopedDirectInstanceRequestRepresentationFactory;
    this.unscopedFrameworkInstanceCreationExpressionFactory =
        unscopedFrameworkInstanceCreationExpressionFactory;
    this.directInstanceBindingRepresentation = directInstanceBindingRepresentation;
    TypeElement rootComponent =
        componentImplementation.rootComponentImplementation().componentDescriptor().typeElement();
    this.isFastInit = compilerOptions.fastInit(rootComponent);
    this.providerField = providerField();
  }

  @Override
  public RequestRepresentation getRequestRepresentation(BindingRequest request) {
    return reentrantComputeIfAbsent(
        requestRepresentations, request, this::getRequestRepresentationUncached);
  }

  private RequestRepresentation getRequestRepresentationUncached(BindingRequest request) {
    switch (request.requestKind()) {
      case INSTANCE:
        return derivedFromFrameworkInstanceRequestRepresentationFactory.create(
            bindingRequest(binding.key(), RequestKind.INSTANCE));

      case PROVIDER:
        return providerRequestRepresentation();

      case LAZY:
      case PROVIDER_OF_LAZY:
        return derivedFromFrameworkInstanceRequestRepresentationFactory.create(
            request);

      default:
        throw new AssertionError(
            String.format("Invalid binding request kind: %s", request.requestKind()));
    }
  }

  /** Supplies a {@link MemberSelect} for a framework instance */
  private FrameworkInstanceSupplier providerField() {
    // In default mode, we always use the static factory creation strategy. In fastInit mode, we
    // prefer to use a SwitchingProvider instead of static factories in order to reduce class
    // loading; however, we allow static factories that can reused across multiple bindings, e.g.
    // {@code MapFactory} or {@code SetFactory}.
    // TODO(bcorso): Consider merging the static factory creation logic into CreationExpressions?
    Optional<MemberSelect> staticMethod = staticFactoryCreation();
    if (staticMethod.isPresent()) {
      return staticMethod::get;
    }

    FrameworkInstanceCreationExpression frameworkInstanceCreationExpression =
        unscopedFrameworkInstanceCreationExpressionFactory.create(binding);

    if (useSwitchingProvider()) {
      // First try to get the instance expression via getRequestRepresentation(). However, if that
      // expression is a DerivedFromFrameworkInstanceRequestRepresentation (e.g. fooProvider.get()),
      // then we can't use it to create an instance within the SwitchingProvider since that would
      // cause a cycle. In such cases, we try to use the unscopedDirectInstanceRequestRepresentation
      // directly, or else fall back to default mode.
      BindingRequest instanceRequest = bindingRequest(binding.key(), RequestKind.INSTANCE);
      if (ProvisionBindingRepresentation.usesDirectInstanceExpression(
          RequestKind.INSTANCE, binding, graph, isFastInit)) {
        frameworkInstanceCreationExpression =
            switchingProviders.newFrameworkInstanceCreationExpression(
                binding,
                directInstanceBindingRepresentation.getRequestRepresentation(instanceRequest));
      } else {
        RequestRepresentation unscopedInstanceExpression =
            unscopedDirectInstanceRequestRepresentationFactory.create(binding);
        frameworkInstanceCreationExpression =
            switchingProviders.newFrameworkInstanceCreationExpression(
                binding,
                ProvisionBindingRepresentation.requiresMethodEncapsulation(binding)
                    ? privateMethodRequestRepresentationFactory.create(
                    instanceRequest, binding, unscopedInstanceExpression)
                    : unscopedInstanceExpression);
      }
    }

    return new FrameworkFieldInitializer(
        componentImplementation,
        binding,
        binding.scope().isPresent()
            ? scope(binding, frameworkInstanceCreationExpression)
            : frameworkInstanceCreationExpression);
  }

  /**
   * If {@code resolvedBindings} is an unscoped provision binding with no factory arguments, then we
   * don't need a field to hold its factory. In that case, this method returns the static member
   * select that returns the factory.
   */
  // TODO(wanyingd): no-op members injector is currently handled in
  // `MembersInjectorProviderCreationExpression`, we should inline the logic here so we won't create
  // an extra field for it.
  private Optional<MemberSelect> staticFactoryCreation() {
    if (binding.dependencies().isEmpty() && !binding.scope().isPresent()) {
      switch (binding.kind()) {
        case PROVISION:
          if (!isFastInit && !binding.requiresModuleInstance()) {
            return Optional.of(StaticMemberSelects.factoryCreateNoArgumentMethod(binding));
          }
          break;
        case INJECTION:
          if (!isFastInit) {
            return Optional.of(StaticMemberSelects.factoryCreateNoArgumentMethod(binding));
          }
          break;
        default:
          return Optional.empty();
      }
    }
    return Optional.empty();
  }

  /**
   * Returns a binding expression for {@link RequestKind#PROVIDER} requests.
   *
   * <p>{@code @Binds} bindings that don't {@linkplain #needsCaching() need to be cached} can use a
   * {@link DelegateRequestRepresentation}.
   *
   * <p>Otherwise, return a {@link FrameworkInstanceRequestRepresentation}.
   */
  private RequestRepresentation providerRequestRepresentation() {
    if (binding.kind().equals(DELEGATE) && !needsCaching()) {
      return delegateRequestRepresentationFactory.create(binding, RequestKind.PROVIDER);
    }
    return providerInstanceRequestRepresentationFactory.create(binding, providerField);
  }

  private boolean useSwitchingProvider() {
    if (!isFastInit) {
      return false;
    }
    switch (binding.kind()) {
      case BOUND_INSTANCE:
      case COMPONENT:
      case COMPONENT_DEPENDENCY:
      case DELEGATE:
        // These binding kinds avoid SwitchingProvider when the backing instance already exists,
        // e.g. a component provider can use FactoryInstance.create(this).
        return false;
      case INJECTION:
      case PROVISION:
      case ASSISTED_INJECTION:
      case ASSISTED_FACTORY:
      case COMPONENT_PROVISION:
      case SUBCOMPONENT_CREATOR:
        return true;
    }
    throw new AssertionError(String.format("No such binding kind: %s", binding.kind()));
  }

  /**
   * Returns {@code true} if the component needs to make sure the provided value is cached.
   *
   * <p>The component needs to cache the value for scoped bindings except for {@code @Binds}
   * bindings whose scope is no stronger than their delegate's.
   */
  private boolean needsCaching() {
    if (!binding.scope().isPresent()) {
      return false;
    }
    if (binding.kind().equals(DELEGATE)) {
      return isBindsScopeStrongerThanDependencyScope(binding, graph);
    }
    return true;
  }

  @AssistedFactory
  static interface Factory {
    FrameworkInstanceBindingRepresentation create(
        ProvisionBinding binding,
        DirectInstanceBindingRepresentation directInstanceBindingRepresentation);
  }
}