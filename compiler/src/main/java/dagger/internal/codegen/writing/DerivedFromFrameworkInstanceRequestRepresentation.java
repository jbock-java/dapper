/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static dagger.internal.codegen.writing.DelegateRequestRepresentation.instanceRequiresCast;
import static java.util.Objects.requireNonNull;

import com.squareup.javapoet.ClassName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.FrameworkType;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.BindingKind;
import dagger.model.RequestKind;

/** A binding expression that depends on a framework instance. */
final class DerivedFromFrameworkInstanceRequestRepresentation extends RequestRepresentation {
  private final ContributionBinding binding;
  private final RequestRepresentation frameworkRequestRepresentation;
  private final RequestKind requestKind;
  private final DaggerTypes types;

  @AssistedInject
  DerivedFromFrameworkInstanceRequestRepresentation(
      @Assisted ContributionBinding binding,
      @Assisted RequestRepresentation frameworkRequestRepresentation,
      @Assisted RequestKind requestKind,
      DaggerTypes types) {
    this.binding = binding;
    this.frameworkRequestRepresentation = requireNonNull(frameworkRequestRepresentation);
    this.requestKind = requestKind;
    this.types = types;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    Expression expression =
        FrameworkType.to(
            requestKind,
            frameworkRequestRepresentation.getDependencyExpression(requestingClass),
            types);
    return requiresTypeCast(expression, requestingClass)
        ? expression.castTo(binding.contributedType())
        : expression;
  }

  @Override
  Expression getDependencyExpressionForComponentMethod(
      ComponentMethodDescriptor componentMethod, ComponentImplementation component) {
    Expression expression =
        FrameworkType.to(
            requestKind,
            frameworkRequestRepresentation.getDependencyExpressionForComponentMethod(
                componentMethod, component),
            types);
    return requiresTypeCast(expression, component.name())
        ? expression.castTo(binding.contributedType())
        : expression;
  }

  private boolean requiresTypeCast(Expression expression, ClassName requestingClass) {
    return binding.kind().equals(BindingKind.DELEGATE)
        && requestKind.equals(RequestKind.INSTANCE)
        && instanceRequiresCast(binding, expression, requestingClass, types);
  }

  @AssistedFactory
  interface Factory {
    DerivedFromFrameworkInstanceRequestRepresentation create(
        ContributionBinding binding,
        RequestRepresentation frameworkRequestRepresentation,
        RequestKind requestKind);
  }
}
