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

import static java.util.Objects.requireNonNull;

import dagger.internal.InstanceFactory;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import io.jbock.javapoet.CodeBlock;
import java.util.function.Supplier;

/**
 * A {@link FrameworkInstanceCreationExpression} that creates an {@link InstanceFactory} for an
 * instance.
 */
final class InstanceFactoryCreationExpression implements FrameworkInstanceCreationExpression {

  private final Supplier<CodeBlock> instanceExpression;

  InstanceFactoryCreationExpression(Supplier<CodeBlock> instanceExpression) {
    this.instanceExpression = requireNonNull(instanceExpression);
  }

  @Override
  public CodeBlock creationExpression() {
    return CodeBlock.of(
        "$T.create($L)",
        InstanceFactory.class,
        instanceExpression.get());
  }
}
