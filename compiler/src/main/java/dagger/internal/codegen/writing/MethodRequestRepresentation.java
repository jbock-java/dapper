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

import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import javax.lang.model.type.TypeMirror;

/** A binding expression that wraps another in a nullary method on the component. */
abstract class MethodRequestRepresentation extends RequestRepresentation {
  private final ShardImplementation shardImplementation;

  protected MethodRequestRepresentation(
      ShardImplementation shardImplementation) {
    this.shardImplementation = shardImplementation;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return Expression.create(
        returnType(),
        requestingClass.equals(shardImplementation.name())
            ? methodCall()
            : CodeBlock.of("$L.$L", shardImplementation.shardFieldReference(), methodCall()));
  }

  /** Returns the return type for the dependency request. */
  protected abstract TypeMirror returnType();

  /** Returns the method call. */
  protected abstract CodeBlock methodCall();
}
