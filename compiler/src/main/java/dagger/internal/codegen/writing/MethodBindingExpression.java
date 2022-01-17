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

import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedParameterSpecs;
import static dagger.internal.codegen.javapoet.CodeBlocks.parameterNames;
import static java.util.Objects.requireNonNull;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.model.BindingKind;
import dagger.model.RequestKind;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/** A binding expression that wraps another in a nullary method on the component. */
abstract class MethodBindingExpression extends BindingExpression {
  private final ShardImplementation shardImplementation;
  private final BindingRequest request;
  private final ContributionBinding binding;
  private final BindingExpression wrappedBindingExpression;
  private final DaggerTypes types;

  protected MethodBindingExpression(
      ShardImplementation shardImplementation,
      BindingRequest request,
      ContributionBinding binding,
      BindingExpression wrappedBindingExpression,
      DaggerTypes types) {
    this.shardImplementation = requireNonNull(shardImplementation);
    this.request = requireNonNull(request);
    this.binding = requireNonNull(binding);
    this.wrappedBindingExpression = requireNonNull(wrappedBindingExpression);
    this.types = requireNonNull(types);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    if (request.frameworkType().isPresent()) {
      // Initializing a framework instance that participates in a cycle requires that the underlying
      // FrameworkInstanceBindingExpression is invoked in order for a cycle to be detected properly.
      // When a MethodBindingExpression wraps a FrameworkInstanceBindingExpression, the wrapped
      // expression will only be invoked once to implement the method body. This is a hack to work
      // around that weirdness - methodImplementation.body() will invoke the framework instance
      // initialization again in case the field is not fully initialized.
      // TODO(b/121196706): use a less hacky approach to fix this bug
      Object unused = methodBody();
    }

    addMethod();

    CodeBlock methodCall =
         binding.kind() == BindingKind.ASSISTED_INJECTION
              // Private methods for assisted injection take assisted parameters as input.
              ? CodeBlock.of(
                  "$N($L)", methodName(), parameterNames(assistedParameterSpecs(binding, types)))
              : CodeBlock.of("$N()", methodName());

    return Expression.create(
        returnType(),
        requestingClass.equals(shardImplementation.name())
            ? methodCall
            : CodeBlock.of("$L.$L", shardImplementation.shardFieldReference(), methodCall));
  }

  /** Adds the method to the component (if necessary) the first time it's called. */
  protected abstract void addMethod();

  /** Returns the name of the method to call. */
  protected abstract String methodName();

  /** The method's body. */
  protected final CodeBlock methodBody() {
    return CodeBlock.of(
        "return $L;",
        wrappedBindingExpression.getDependencyExpression(shardImplementation.name()).codeBlock());
  }

  /** The method's body if this method is a component method. */
  protected final CodeBlock methodBodyForComponentMethod(
      ComponentMethodDescriptor componentMethod) {
    return CodeBlock.of(
        "return $L;",
        wrappedBindingExpression
            .getDependencyExpressionForComponentMethod(
                componentMethod, shardImplementation.getComponentImplementation())
            .codeBlock());
  }

  /** Returns the return type for the dependency request. */
  protected TypeMirror returnType() {
    if (request.isRequestKind(RequestKind.INSTANCE)
        && binding.contributedPrimitiveType().isPresent()) {
      return binding.contributedPrimitiveType().get();
    }

    if (matchingComponentMethod().isPresent()) {
      // Component methods are part of the user-defined API, and thus we must use the user-defined
      // type.
      return matchingComponentMethod().get().resolvedReturnType(types);
    }

    TypeMirror requestedType = request.requestedType(binding.contributedType(), types);
    return types.accessibleType(requestedType, shardImplementation.name());
  }

  private Optional<ComponentMethodDescriptor> matchingComponentMethod() {
    return shardImplementation.componentDescriptor().firstMatchingComponentMethod(request);
  }
}
