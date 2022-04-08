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

import static dagger.internal.codegen.base.Preconditions.checkState;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.writing.ComponentImplementation.MethodSpecKind.MEMBERS_INJECTION_METHOD;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.InjectionMethods.InjectionSiteMethod;
import dagger.spi.model.Key;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.MethodSpec;
import io.jbock.javapoet.ParameterSpec;
import io.jbock.javapoet.TypeName;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** Manages the member injection methods for a component. */
@PerComponentImplementation
final class MembersInjectionMethods {
  private final Map<Key, Expression> injectMethodExpressions = new LinkedHashMap<>();
  private final Map<Key, Expression> experimentalInjectMethodExpressions = new LinkedHashMap<>();
  private final ComponentImplementation componentImplementation;
  private final ComponentRequestRepresentations bindingExpressions;
  private final BindingGraph graph;
  private final DaggerElements elements;
  private final DaggerTypes types;
  private final KotlinMetadataUtil metadataUtil;

  @Inject
  MembersInjectionMethods(
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations bindingExpressions,
      BindingGraph graph,
      DaggerElements elements,
      DaggerTypes types,
      KotlinMetadataUtil metadataUtil) {
    this.componentImplementation = componentImplementation;
    this.bindingExpressions = bindingExpressions;
    this.graph = graph;
    this.elements = elements;
    this.types = types;
    this.metadataUtil = metadataUtil;
  }

  /**
   * Returns the members injection {@code Expression} for the given {@code Key}, creating it if
   * necessary.
   */
  Expression getInjectExpression(Key key, CodeBlock instance, ClassName requestingClass) {
    Binding binding =
        graph.localMembersInjectionBinding(key).isPresent()
            ? graph.localMembersInjectionBinding(key).get()
            : graph.localContributionBinding(key).get();
    Expression expression =
        reentrantComputeIfAbsent(
            injectMethodExpressions, key, k -> injectMethodExpression(binding, false));
    ShardImplementation shardImplementation = componentImplementation.shardImplementation(binding);
    return Expression.create(
        expression.type(),
        shardImplementation.name().equals(requestingClass)
            ? CodeBlock.of("$L($L)", expression.codeBlock(), instance)
            : CodeBlock.of(
                "$L.$L($L)",
                shardImplementation.shardFieldReference(),
                expression.codeBlock(),
                instance));
  }

  /**
   * Returns the members injection {@code Expression} for the given {@code Key}, creating it if
   * necessary.
   */
  Expression getInjectExpressionExperimental(
      ProvisionBinding provisionBinding, CodeBlock instance, ClassName requestingClass) {
    checkState(
        componentImplementation.compilerMode().isExperimentalMergedMode(),
        "Compiler mode should be experimentalMergedMode!");
    Expression expression =
        reentrantComputeIfAbsent(
            experimentalInjectMethodExpressions,
            provisionBinding.key(),
            k -> injectMethodExpression(provisionBinding, true));
    return Expression.create(
        expression.type(), CodeBlock.of("$L($L, dependencies)", expression.codeBlock(), instance));
  }

  private Expression injectMethodExpression(Binding binding, boolean useStaticInjectionMethod) {
    ShardImplementation shardImplementation = componentImplementation.shardImplementation(binding);
    TypeMirror keyType = binding.key().type().java();
    TypeMirror membersInjectedType =
        isTypeAccessibleFrom(keyType, shardImplementation.name().packageName())
            ? keyType
            : elements.getTypeElement(TypeName.OBJECT).asType();
    TypeName membersInjectedTypeName = TypeName.get(membersInjectedType);
    String bindingTypeName = getSimpleName(binding.bindingTypeElement().get());
    // TODO(ronshapiro): include type parameters in this name e.g. injectFooOfT, and outer class
    // simple names Foo.Builder -> injectFooBuilder
    String methodName = shardImplementation.getUniqueMethodName("inject" + bindingTypeName);
    ParameterSpec parameter = ParameterSpec.builder(membersInjectedTypeName, "instance").build();
    MethodSpec.Builder methodBuilder =
        useStaticInjectionMethod
            ? methodBuilder(methodName)
                .addModifiers(PRIVATE, STATIC)
                .returns(membersInjectedTypeName)
                .addParameter(parameter)
                .addParameter(Object[].class, "dependencies")
            : methodBuilder(methodName)
                .addModifiers(PRIVATE)
                .returns(membersInjectedTypeName)
                .addParameter(parameter);
    TypeElement canIgnoreReturnValue =
        elements.getTypeElement("com.google.errorprone.annotations.CanIgnoreReturnValue");
    if (canIgnoreReturnValue != null) {
      methodBuilder.addAnnotation(ClassName.get(canIgnoreReturnValue));
    }
    CodeBlock instance = CodeBlock.of("$N", parameter);
    methodBuilder.addCode(
        InjectionSiteMethod.invokeAll(
            injectionSites(binding),
            shardImplementation.name(),
            instance,
            membersInjectedType,
            request ->
                (useStaticInjectionMethod
                        ? bindingExpressions
                            .getExperimentalSwitchingProviderDependencyRepresentation(
                                bindingRequest(request))
                            .getDependencyExpression(request.kind(), (ProvisionBinding) binding)
                        : bindingExpressions.getDependencyArgumentExpression(
                            request, shardImplementation.name()))
                    .codeBlock(),
            types,
            metadataUtil));
    methodBuilder.addStatement("return $L", instance);

    MethodSpec method = methodBuilder.build();
    shardImplementation.addMethod(MEMBERS_INJECTION_METHOD, method);
    return Expression.create(membersInjectedType, CodeBlock.of("$N", method));
  }

  private static ImmutableSet<InjectionSite> injectionSites(Binding binding) {
    if (binding instanceof ProvisionBinding) {
      return ((ProvisionBinding) binding).injectionSites();
    } else if (binding instanceof MembersInjectionBinding) {
      return ((MembersInjectionBinding) binding).injectionSites();
    }
    throw new IllegalArgumentException(binding.key().toString());
  }
}
