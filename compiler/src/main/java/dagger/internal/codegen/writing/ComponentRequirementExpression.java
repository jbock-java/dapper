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

import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;

/**
 * A factory for expressions of {@code ComponentRequirement}s in the generated component. This is
 * <em>not</em> a {@code RequestRepresentation}, since {@code ComponentRequirement}s do not have a
 * {@code dagger.spi.model.Key}. See {@code ComponentRequirementRequestRepresentation} for binding
 * expressions that are themselves a component requirement.
 */
interface ComponentRequirementExpression {
  /**
   * Returns an expression for the {@code ComponentRequirement} to be used when implementing a
   * component method. This may add a field or method to the component in order to reference the
   * component requirement outside of the {@code initialize()} methods.
   */
  CodeBlock getExpression(ClassName requestingClass);

  /**
   * Returns an expression for the {@code ComponentRequirement} to be used only within {@code
   * initialize()} methods, where the constructor parameters are available.
   *
   * <p>When accessing this expression from a subcomponent, this may cause a field to be initialized
   * or a method to be added in the component that owns this {@code ComponentRequirement}.
   */
  default CodeBlock getExpressionDuringInitialization(ClassName requestingClass) {
    return getExpression(requestingClass);
  }

  /**
   * Returns the expression for the {@code ComponentRequirement} to be used when reimplementing a
   * modifiable module method.
   */
  default CodeBlock getModifiableModuleMethodExpression(ClassName requestingClass) {
    return CodeBlock.of("return $L", getExpression(requestingClass));
  }
}
