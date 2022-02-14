/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.rawTypeName;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.writing.InjectionMethods.ProvisionMethod.requiresInjectionMethod;
import static io.jbock.auto.common.MoreElements.asExecutable;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.writing.InjectionMethods.ProvisionMethod;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.RequestKind;
import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.TypeName;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * A binding expression that invokes methods or constructors directly (without attempting to scope)
 * {@link RequestKind#INSTANCE} requests.
 */
final class SimpleMethodRequestRepresentation extends RequestRepresentation {
  private final CompilerOptions compilerOptions;
  private final ProvisionBinding provisionBinding;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final ComponentImplementation.ShardImplementation shardImplementation;

  @AssistedInject
  SimpleMethodRequestRepresentation(
      @Assisted ProvisionBinding binding,
      CompilerOptions compilerOptions,
      ComponentRequestRepresentations componentRequestRepresentations,
      ComponentRequirementExpressions componentRequirementExpressions,
      ComponentImplementation componentImplementation) {
    this.compilerOptions = compilerOptions;
    this.provisionBinding = binding;
    Preconditions.checkArgument(provisionBinding.bindingElement().isPresent());
    this.componentRequestRepresentations = componentRequestRepresentations;
    this.componentRequirementExpressions = componentRequirementExpressions;
    this.shardImplementation = componentImplementation.shardImplementation(binding);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return requiresInjectionMethod(provisionBinding, compilerOptions, requestingClass)
        ? invokeInjectionMethod(requestingClass)
        : invokeMethod(requestingClass);
  }

  private Expression invokeMethod(ClassName requestingClass) {
    // TODO(dpb): align this with the contents of InlineMethods.create
    CodeBlock arguments =
        makeParametersCodeBlock(
            ProvisionMethod.invokeArguments(
                provisionBinding,
                request -> dependencyArgument(request, requestingClass).codeBlock(),
                shardImplementation::getUniqueFieldNameForAssistedParam));
    ExecutableElement method = asExecutable(provisionBinding.bindingElement().orElseThrow());
    CodeBlock invocation;
    switch (method.getKind()) {
      case CONSTRUCTOR:
        invocation = CodeBlock.of("new $T($L)", constructorTypeName(requestingClass), arguments);
        break;
      case METHOD:
        CodeBlock module = moduleReference(requestingClass).orElseGet(() ->
            CodeBlock.of("$T", provisionBinding.bindingTypeElement().orElseThrow()));
        invocation = CodeBlock.of("$L.$L($L)", module, method.getSimpleName(), arguments);
        break;
      default:
        throw new IllegalStateException();
    }

    return Expression.create(simpleMethodReturnType(), invocation);
  }

  private TypeName constructorTypeName(ClassName requestingClass) {
    DeclaredType type = MoreTypes.asDeclared(provisionBinding.key().type().java());
    TypeName typeName = TypeName.get(type);
    if (type.getTypeArguments().stream()
        .allMatch(t -> isTypeAccessibleFrom(t, requestingClass.packageName()))) {
      return typeName;
    }
    return rawTypeName(typeName);
  }

  private Expression invokeInjectionMethod(ClassName requestingClass) {
    return injectMembers(
        ProvisionMethod.invoke(
            provisionBinding,
            request -> dependencyArgument(request, requestingClass).codeBlock(),
            shardImplementation::getUniqueFieldNameForAssistedParam,
            requestingClass,
            moduleReference(requestingClass),
            compilerOptions
        ));
  }

  private Expression dependencyArgument(DependencyRequest dependency, ClassName requestingClass) {
    return componentRequestRepresentations.getDependencyArgumentExpression(dependency, requestingClass);
  }

  private Expression injectMembers(CodeBlock instance) {
    return Expression.create(simpleMethodReturnType(), instance);
  }

  private Optional<CodeBlock> moduleReference(ClassName requestingClass) {
    return provisionBinding.requiresModuleInstance()
        ? provisionBinding
        .contributingModule()
        .map(Element::asType)
        .map(ComponentRequirement::forModule)
        .map(module -> componentRequirementExpressions.getExpression(module, requestingClass))
        : Optional.empty();
  }

  private TypeMirror simpleMethodReturnType() {
    return provisionBinding.contributedPrimitiveType().orElse(provisionBinding.key().type().java());
  }

  @AssistedFactory
  interface Factory {
    SimpleMethodRequestRepresentation create(ProvisionBinding binding);
  }
}
