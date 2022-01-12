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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Key;
import dagger.model.RequestKind;

final class ImmediateFutureBindingExpression extends BindingExpression {
  private final Key key;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final DaggerTypes types;

  @AssistedInject
  ImmediateFutureBindingExpression(
      @Assisted Key key,
      ComponentBindingExpressions componentBindingExpressions,
      DaggerTypes types) {
    this.key = key;
    this.componentBindingExpressions = requireNonNull(componentBindingExpressions);
    this.types = requireNonNull(types);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return Expression.create(
        types.wrapType(key.type(), ListenableFuture.class),
        CodeBlock.of("$T.immediateFuture($L)", Futures.class, instanceExpression(requestingClass)));
  }

  private CodeBlock instanceExpression(ClassName requestingClass) {
    Expression expression =
        componentBindingExpressions.getDependencyExpression(
            bindingRequest(key, RequestKind.INSTANCE), requestingClass);
    return expression.codeBlock();
  }

  @AssistedFactory
  interface Factory {
    ImmediateFutureBindingExpression create(Key key);
  }
}
