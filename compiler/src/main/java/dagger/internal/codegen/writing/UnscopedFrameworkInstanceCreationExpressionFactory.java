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

import io.jbock.javapoet.CodeBlock;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import jakarta.inject.Inject;

/**
 * A factory for creating unscoped creation expressions for a provision or production binding.
 *
 * <p>A creation expression is responsible for creating the factory for a given binding (e.g. by
 * calling the generated factory create method, {@code Foo_Factory.create(...)}). Note that this
 * class does not handle scoping of these factories (e.g. wrapping in {@code
 * DoubleCheck.provider()}).
 */
final class UnscopedFrameworkInstanceCreationExpressionFactory {
  private final ComponentImplementation componentImplementation;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final AnonymousProviderCreationExpression.Factory
      anonymousProviderCreationExpressionFactory;
  private final DelegatingFrameworkInstanceCreationExpression.Factory
      delegatingFrameworkInstanceCreationExpressionFactory;
  private final DependencyMethodProviderCreationExpression.Factory
      dependencyMethodProviderCreationExpressionFactory;
  private final InjectionOrProvisionProviderCreationExpression.Factory
      injectionOrProvisionProviderCreationExpressionFactory;
  private final MembersInjectorProviderCreationExpression.Factory
      membersInjectorProviderCreationExpressionFactory;

  @Inject
  UnscopedFrameworkInstanceCreationExpressionFactory(
      ComponentImplementation componentImplementation,
      ComponentRequirementExpressions componentRequirementExpressions,
      AnonymousProviderCreationExpression.Factory anonymousProviderCreationExpressionFactory,
      DelegatingFrameworkInstanceCreationExpression.Factory
          delegatingFrameworkInstanceCreationExpressionFactory,
      DependencyMethodProviderCreationExpression.Factory
          dependencyMethodProviderCreationExpressionFactory,
      InjectionOrProvisionProviderCreationExpression.Factory
          injectionOrProvisionProviderCreationExpressionFactory,
      MembersInjectorProviderCreationExpression.Factory
          membersInjectorProviderCreationExpressionFactory) {
    this.componentImplementation = componentImplementation;
    this.componentRequirementExpressions = componentRequirementExpressions;
    this.anonymousProviderCreationExpressionFactory = anonymousProviderCreationExpressionFactory;
    this.delegatingFrameworkInstanceCreationExpressionFactory =
        delegatingFrameworkInstanceCreationExpressionFactory;
    this.dependencyMethodProviderCreationExpressionFactory =
        dependencyMethodProviderCreationExpressionFactory;
    this.injectionOrProvisionProviderCreationExpressionFactory =
        injectionOrProvisionProviderCreationExpressionFactory;
    this.membersInjectorProviderCreationExpressionFactory =
        membersInjectorProviderCreationExpressionFactory;
  }

  /**
   * Returns an unscoped creation expression for a {@link jakarta.inject.Provider} for provision
   * bindings or a {@code dagger.producers.Producer} for production bindings.
   */
  FrameworkInstanceCreationExpression create(ContributionBinding binding) {
    switch (binding.kind()) {
      case COMPONENT:
        // The cast can be removed when we drop java 7 source support
        return new InstanceFactoryCreationExpression(
            () ->
                CodeBlock.of(
                    "($T) $L",
                    binding.key().type().java(),
                    componentImplementation.componentFieldReference()));

      case BOUND_INSTANCE:
        return instanceFactoryCreationExpression(
            binding, ComponentRequirement.forBoundInstance(binding));

      case COMPONENT_DEPENDENCY:
        return instanceFactoryCreationExpression(
            binding, ComponentRequirement.forDependency(binding.key().type().xprocessing()));

      case COMPONENT_PROVISION:
        return dependencyMethodProviderCreationExpressionFactory.create((ProvisionBinding) binding);

      case SUBCOMPONENT_CREATOR:
        return anonymousProviderCreationExpressionFactory.create(binding);

      case ASSISTED_FACTORY:
      case ASSISTED_INJECTION:
      case INJECTION:
      case PROVISION:
        return injectionOrProvisionProviderCreationExpressionFactory.create(binding);

      case DELEGATE:
        return delegatingFrameworkInstanceCreationExpressionFactory.create(binding);

      case MEMBERS_INJECTOR:
        return membersInjectorProviderCreationExpressionFactory.create((ProvisionBinding) binding);

      default:
        throw new AssertionError(binding);
    }
  }

  private InstanceFactoryCreationExpression instanceFactoryCreationExpression(
      ContributionBinding binding, ComponentRequirement componentRequirement) {
    return new InstanceFactoryCreationExpression(
        binding.nullableType().isPresent(),
        () ->
            componentRequirementExpressions.getExpressionDuringInitialization(
                componentRequirement, componentImplementation.name()));
  }
}
