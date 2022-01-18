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

import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static java.util.Objects.requireNonNull;

import com.squareup.javapoet.ClassName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.FrameworkType;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;

/** A binding expression that depends on a framework instance. */
final class DerivedFromFrameworkInstanceRequestRepresentation extends RequestRepresentation {
  private final BindingRequest bindingRequest;
  private final BindingRequest frameworkRequest;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final DaggerTypes types;

  @AssistedInject
  DerivedFromFrameworkInstanceRequestRepresentation(
      @Assisted BindingRequest bindingRequest,
      ComponentRequestRepresentations componentRequestRepresentations,
      DaggerTypes types) {
    this.bindingRequest = requireNonNull(bindingRequest);
    this.frameworkRequest = bindingRequest(bindingRequest.key(), FrameworkType.PROVIDER);
    this.componentRequestRepresentations = componentRequestRepresentations;
    this.types = types;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return FrameworkType.to(
        bindingRequest.requestKind(),
        componentRequestRepresentations.getDependencyExpression(frameworkRequest, requestingClass),
        types);
  }

  @Override
  Expression getDependencyExpressionForComponentMethod(
      ComponentMethodDescriptor componentMethod, ComponentImplementation component) {
    Expression frameworkInstance =
        componentRequestRepresentations.getDependencyExpressionForComponentMethod(
            frameworkRequest, componentMethod, component);
    return FrameworkType.to(bindingRequest.requestKind(), frameworkInstance, types);
  }

  @AssistedFactory
  interface Factory {
    DerivedFromFrameworkInstanceRequestRepresentation create(BindingRequest request);
  }
}
