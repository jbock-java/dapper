/*
 * Copyright (C) 2020 The Dagger Authors.
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

import static dagger.internal.codegen.base.Util.getOnlyElement;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedFactoryMethod;
import static dagger.internal.codegen.writing.AssistedInjectionParameters.assistedFactoryParameterSpecs;
import static dagger.internal.codegen.xprocessing.XConverters.toXProcessing;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static io.jbock.auto.common.MoreElements.asType;
import static io.jbock.auto.common.MoreTypes.asDeclared;
import static java.util.Objects.requireNonNull;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.xprocessing.MethodSpecs;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.spi.model.DependencyRequest;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.MethodSpec;
import io.jbock.javapoet.TypeName;
import io.jbock.javapoet.TypeSpec;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

/**
 * A {@link RequestRepresentation} for {@link
 * dagger.assisted.AssistedFactory} methods.
 */
final class AssistedFactoryRequestRepresentation extends RequestRepresentation {
  private final XProcessingEnv processingEnv;
  private final ProvisionBinding binding;
  private final BindingGraph graph;
  private final SimpleMethodRequestRepresentation.Factory simpleMethodRequestRepresentationFactory;
  private final ComponentImplementation componentImplementation;

  @AssistedInject
  AssistedFactoryRequestRepresentation(
      XProcessingEnv processingEnv,
      @Assisted ProvisionBinding binding,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      SimpleMethodRequestRepresentation.Factory simpleMethodRequestRepresentationFactory) {
    this.processingEnv = processingEnv;
    this.binding = requireNonNull(binding);
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.simpleMethodRequestRepresentationFactory = simpleMethodRequestRepresentationFactory;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    // An assisted factory binding should have a single request for an assisted injection type.
    DependencyRequest assistedInjectionRequest = getOnlyElement(binding.provisionDependencies());
    // Get corresponding assisted injection binding.
    Optional<Binding> localBinding = graph.localContributionBinding(assistedInjectionRequest.key());
    Preconditions.checkArgument(
        localBinding.isPresent(),
        "assisted factory should have a dependency on an assisted injection binding");
    Expression assistedInjectionExpression =
        simpleMethodRequestRepresentationFactory
            .create((ProvisionBinding) localBinding.get())
            .getDependencyExpression(requestingClass.peerClass(""));
    return Expression.create(
        assistedInjectionExpression.type(),
        CodeBlock.of("$L", anonymousfactoryImpl(localBinding.get(), assistedInjectionExpression)));
  }

  private TypeSpec anonymousfactoryImpl(
      Binding assistedBinding, Expression assistedInjectionExpression) {
    XTypeElement factory =
        asTypeElement(toXProcessing(binding.bindingElement().orElseThrow(), processingEnv));
    XType factoryType = binding.key().type().xprocessing();
    XMethodElement factoryMethod = assistedFactoryMethod(factory);

    // We can't use MethodSpec.overriding directly because we need to control the parameter names.
    MethodSpec factoryOverride = MethodSpecs.overriding(factoryMethod, factoryType).build();
    TypeSpec.Builder builder =
        TypeSpec.anonymousClassBuilder("")
            .addMethod(
                MethodSpec.methodBuilder(factoryMethod.getName())
                    .addModifiers(factoryOverride.modifiers)
                    .addTypeVariables(factoryOverride.typeVariables)
                    .returns(factoryOverride.returnType)
                    .addAnnotations(factoryOverride.annotations)
                    .addExceptions(factoryOverride.exceptions)
                    .addParameters(
                        assistedFactoryParameterSpecs(
                            binding,
                            processingEnv,
                            componentImplementation.shardImplementation(assistedBinding)))
                    .addStatement("return $L", assistedInjectionExpression.codeBlock())
                    .build());

    if (factory.isInterface()) {
      builder.addSuperinterface(factoryType.getTypeName());
    } else {
      builder.superclass(factoryType.getTypeName());
    }

    return builder.build();
  }

  @AssistedFactory
  interface Factory {
    AssistedFactoryRequestRepresentation create(ProvisionBinding binding);
  }
}
