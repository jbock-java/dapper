/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static dagger.internal.codegen.base.Preconditions.checkNotNull;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.javapoet.Expression;
import io.jbock.javapoet.ClassName;

/**
 * A binding expression for instances bound with {@code dagger.BindsInstance} and instances of
 * {@code dagger.Component#dependencies() component} and {@code
 * dagger.producers.ProductionComponent#dependencies() production component dependencies}.
 */
final class ComponentRequirementRequestRepresentation extends RequestRepresentation {
  private final ComponentRequirement componentRequirement;
  private final ComponentRequirementExpressions componentRequirementExpressions;

  @AssistedInject
  ComponentRequirementRequestRepresentation(
      @Assisted ContributionBinding binding,
      @Assisted ComponentRequirement componentRequirement,
      ComponentRequirementExpressions componentRequirementExpressions) {
    this.componentRequirement = checkNotNull(componentRequirement);
    this.componentRequirementExpressions = componentRequirementExpressions;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return Expression.create(
        componentRequirement.type(),
        componentRequirementExpressions.getExpression(componentRequirement, requestingClass));
  }

  @AssistedFactory
  static interface Factory {
    ComponentRequirementRequestRepresentation create(
        ContributionBinding binding, ComponentRequirement componentRequirement);
  }
}
