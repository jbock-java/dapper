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

import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.model.RequestKind;
import jakarta.inject.Inject;
import java.util.Optional;

/**
 * A factory for creating a binding expression for an unscoped instance.
 *
 * <p>Note that these binding expressions are for getting "direct" instances -- i.e. instances that
 * are created via constructors or modules (e.g. {@code new Foo()} or {@code
 * FooModule.provideFoo()}) as opposed to an instance created from calling a getter on a framework
 * type (e.g. {@code fooProvider.get()}). See {@link FrameworkInstanceRequestRepresentation} for binding
 * expressions that are created from framework types.
 */
final class UnscopedDirectInstanceRequestRepresentationFactory {
  private final AssistedFactoryRequestRepresentation.Factory assistedFactoryBindingExpressionFactory;
  private final ComponentInstanceRequestRepresentation.Factory
      componentInstanceBindingExpressionFactory;
  private final ComponentProvisionRequestRepresentation.Factory
      componentProvisionBindingExpressionFactory;
  private final ComponentRequirementRequestRepresentation.Factory
      componentRequirementBindingExpressionFactory;
  private final DelegateRequestRepresentation.Factory delegateBindingExpressionFactory;
  private final SimpleMethodRequestRepresentation.Factory simpleMethodBindingExpressionFactory;
  private final SubcomponentCreatorRequestRepresentation.Factory
      subcomponentCreatorBindingExpressionFactory;

  @Inject
  UnscopedDirectInstanceRequestRepresentationFactory(
      AssistedFactoryRequestRepresentation.Factory assistedFactoryBindingExpressionFactory,
      ComponentInstanceRequestRepresentation.Factory componentInstanceBindingExpressionFactory,
      ComponentProvisionRequestRepresentation.Factory componentProvisionBindingExpressionFactory,
      ComponentRequirementRequestRepresentation.Factory componentRequirementBindingExpressionFactory,
      DelegateRequestRepresentation.Factory delegateBindingExpressionFactory,
      SimpleMethodRequestRepresentation.Factory simpleMethodBindingExpressionFactory,
      SubcomponentCreatorRequestRepresentation.Factory subcomponentCreatorBindingExpressionFactory) {
    this.assistedFactoryBindingExpressionFactory = assistedFactoryBindingExpressionFactory;
    this.componentInstanceBindingExpressionFactory = componentInstanceBindingExpressionFactory;
    this.componentProvisionBindingExpressionFactory = componentProvisionBindingExpressionFactory;
    this.componentRequirementBindingExpressionFactory =
        componentRequirementBindingExpressionFactory;
    this.delegateBindingExpressionFactory = delegateBindingExpressionFactory;
    this.simpleMethodBindingExpressionFactory = simpleMethodBindingExpressionFactory;
    this.subcomponentCreatorBindingExpressionFactory = subcomponentCreatorBindingExpressionFactory;
  }

  /** Returns a direct, unscoped binding expression for a {@link RequestKind#INSTANCE} request. */
  RequestRepresentation create(ContributionBinding binding) {
    switch (binding.kind()) {
      case DELEGATE:
        return delegateBindingExpressionFactory.create(binding, RequestKind.INSTANCE);

      case COMPONENT:
        return componentInstanceBindingExpressionFactory.create(binding);

      case COMPONENT_DEPENDENCY:
        return componentRequirementBindingExpressionFactory.create(
            binding, ComponentRequirement.forDependency(binding.key().type()));

      case COMPONENT_PROVISION:
        return componentProvisionBindingExpressionFactory.create((ProvisionBinding) binding);

      case SUBCOMPONENT_CREATOR:
        return subcomponentCreatorBindingExpressionFactory.create(binding);

      case BOUND_INSTANCE:
        return componentRequirementBindingExpressionFactory.create(
            binding, ComponentRequirement.forBoundInstance(binding));

      case ASSISTED_FACTORY:
        return assistedFactoryBindingExpressionFactory.create((ProvisionBinding) binding);

      case ASSISTED_INJECTION:
      case INJECTION:
      case PROVISION:
        return simpleMethodBindingExpressionFactory.create((ProvisionBinding) binding);
    }
    throw new AssertionError("Unexpected binding kind: " + binding.kind());
  }
}
