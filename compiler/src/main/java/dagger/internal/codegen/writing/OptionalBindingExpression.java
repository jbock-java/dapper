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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.OptionalType;
import dagger.internal.codegen.base.OptionalType.OptionalKind;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;

/** A binding expression for optional bindings. */
final class OptionalBindingExpression extends SimpleInvocationBindingExpression {
  private final ProvisionBinding binding;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final DaggerTypes types;

  @AssistedInject
  OptionalBindingExpression(
      @Assisted ProvisionBinding binding,
      ComponentBindingExpressions componentBindingExpressions,
      DaggerTypes types) {
    super(binding);
    this.binding = binding;
    this.componentBindingExpressions = componentBindingExpressions;
    this.types = types;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    OptionalType optionalType = OptionalType.from(binding.key());
    OptionalKind optionalKind = optionalType.kind();
    if (binding.dependencies().isEmpty()) {
      return Expression.create(binding.key().type(), optionalKind.absentValueExpression());
    }
    DependencyRequest dependency = getOnlyElement(binding.dependencies());

    CodeBlock dependencyExpression =
        componentBindingExpressions
            .getDependencyExpression(bindingRequest(dependency), requestingClass)
            .codeBlock();

    // If the dependency type is inaccessible, then we have to use Optional.<Object>of(...), or else
    // we will get "incompatible types: inference variable has incompatible bounds.
    return isTypeAccessibleFrom(dependency.key().type(), requestingClass.packageName())
        ? Expression.create(
        binding.key().type(), optionalKind.presentExpression(dependencyExpression))
        : Expression.create(
        types.erasure(binding.key().type()),
        optionalKind.presentObjectExpression(dependencyExpression));
  }

  @Override
  boolean requiresMethodEncapsulation() {
    // TODO(dpb): Maybe require it for present bindings.
    return false;
  }

  @AssistedFactory
  static interface Factory {
    OptionalBindingExpression create(ProvisionBinding binding);
  }
}
