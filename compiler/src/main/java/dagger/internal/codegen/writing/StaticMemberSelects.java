/*
 * Copyright (C) 2021 The Dagger Authors.
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

import static dagger.internal.codegen.binding.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.FACTORY;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.type.TypeKind.DECLARED;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingType;
import dagger.internal.codegen.javapoet.CodeBlocks;
import java.util.List;
import javax.lang.model.type.TypeMirror;

/** Helper class for static member select creation. */
final class StaticMemberSelects {

  /**
   * Returns a {@link MemberSelect} for the instance of a {@code create()} method on a factory with
   * no arguments.
   */
  static MemberSelect factoryCreateNoArgumentMethod(Binding binding) {
    Preconditions.checkArgument(
        binding.bindingType().equals(BindingType.PROVISION),
        "Invalid binding type: %s",
        binding.bindingType());
    Preconditions.checkArgument(
        binding.dependencies().isEmpty() && binding.scope().isEmpty(),
        "%s should have no dependencies and be unscoped to create a no argument factory.",
        binding);

    ClassName factoryName = generatedClassNameForBinding(binding);
    TypeMirror keyType = binding.key().type().java();
    if (keyType.getKind().equals(DECLARED)) {
      List<TypeVariableName> typeVariables = bindingTypeElementTypeVariableNames(binding);
      if (!typeVariables.isEmpty()) {
        List<? extends TypeMirror> typeArguments = MoreTypes.asDeclared(keyType).getTypeArguments();
        return new ParameterizedStaticMethod(
            factoryName, List.copyOf(typeArguments), CodeBlock.of("create()"), FACTORY);
      }
    }
    return new StaticMethod(factoryName, CodeBlock.of("create()"));
  }

  private static final class StaticMethod extends MemberSelect {
    private final CodeBlock methodCodeBlock;

    StaticMethod(ClassName owningClass, CodeBlock methodCodeBlock) {
      super(owningClass, true);
      this.methodCodeBlock = requireNonNull(methodCodeBlock);
    }

    @Override
    CodeBlock getExpressionFor(ClassName usingClass) {
      return owningClass().equals(usingClass)
          ? methodCodeBlock
          : CodeBlock.of("$T.$L", owningClass(), methodCodeBlock);
    }
  }

  private static final class ParameterizedStaticMethod extends MemberSelect {
    private final List<TypeMirror> typeParameters;
    private final CodeBlock methodCodeBlock;
    private final ClassName rawReturnType;

    ParameterizedStaticMethod(
        ClassName owningClass,
        List<TypeMirror> typeParameters,
        CodeBlock methodCodeBlock,
        ClassName rawReturnType) {
      super(owningClass, true);
      this.typeParameters = typeParameters;
      this.methodCodeBlock = methodCodeBlock;
      this.rawReturnType = rawReturnType;
    }

    @Override
    CodeBlock getExpressionFor(ClassName usingClass) {
      boolean accessible = true;
      for (TypeMirror typeParameter : typeParameters) {
        accessible &= isTypeAccessibleFrom(typeParameter, usingClass.packageName());
      }

      if (accessible) {
        return CodeBlock.of(
            "$T.<$L>$L",
            owningClass(),
            typeParameters.stream().map(CodeBlocks::type).collect(toParametersCodeBlock()),
            methodCodeBlock);
      } else {
        return CodeBlock.of("(($T) $T.$L)", rawReturnType, owningClass(), methodCodeBlock);
      }
    }
  }

  private StaticMemberSelects() {}
}