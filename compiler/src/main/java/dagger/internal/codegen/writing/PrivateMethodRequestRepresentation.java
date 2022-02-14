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

import static dagger.internal.codegen.writing.ComponentImplementation.MethodSpecKind.PRIVATE_METHOD;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.Modifier.PRIVATE;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.spi.model.RequestKind;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.TypeName;
import javax.lang.model.type.TypeMirror;

/**
 * A binding expression that wraps the dependency expressions in a private, no-arg method.
 *
 * <p>Dependents of this binding expression will just call the no-arg private method.
 */
final class PrivateMethodRequestRepresentation extends MethodRequestRepresentation {
  private final ShardImplementation shardImplementation;
  private final ContributionBinding binding;
  private final BindingRequest request;
  private final RequestRepresentation wrappedRequestRepresentation;
  private final DaggerTypes types;
  private String methodName;

  @AssistedInject
  PrivateMethodRequestRepresentation(
      @Assisted BindingRequest request,
      @Assisted ContributionBinding binding,
      @Assisted RequestRepresentation wrappedRequestRepresentation,
      ComponentImplementation componentImplementation,
      DaggerTypes types) {
    super(componentImplementation.shardImplementation(binding));
    this.binding = requireNonNull(binding);
    this.request = requireNonNull(request);
    this.wrappedRequestRepresentation = requireNonNull(wrappedRequestRepresentation);
    this.shardImplementation = componentImplementation.shardImplementation(binding);
    this.types = types;
  }

  @Override
  protected CodeBlock methodCall() {
    return CodeBlock.of("$N()", methodName());
  }

  @Override
  protected TypeMirror returnType() {
    if (request.isRequestKind(RequestKind.INSTANCE)
        && binding.contributedPrimitiveType().isPresent()) {
      return binding.contributedPrimitiveType().orElseThrow();
    }

    TypeMirror requestedType = request.requestedType(binding.contributedType(), types);
    return types.accessibleType(requestedType, shardImplementation.name());
  }

  private String methodName() {
    if (methodName == null) {
      // Have to set methodName field before implementing the method in order to handle recursion.
      methodName = shardImplementation.getUniqueMethodName(request);

      // TODO(bcorso): Fix the order that these generated methods are written to the component.
      shardImplementation.addMethod(
          PRIVATE_METHOD,
          methodBuilder(methodName)
              .addModifiers(PRIVATE)
              .returns(TypeName.get(returnType()))
              .addStatement(
                  "return $L",
                  wrappedRequestRepresentation
                      .getDependencyExpression(shardImplementation.name())
                      .codeBlock())
              .build());
    }
    return methodName;
  }

  @AssistedFactory
  interface Factory {
    PrivateMethodRequestRepresentation create(
        BindingRequest request,
        ContributionBinding binding,
        RequestRepresentation wrappedRequestRepresentation);
  }
}
