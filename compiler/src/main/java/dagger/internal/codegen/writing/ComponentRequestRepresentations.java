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

import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static java.util.Objects.requireNonNull;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.FrameworkType;
import dagger.internal.codegen.binding.FrameworkTypeMapper;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.RequestKind;
import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.MethodSpec;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/** A central repository of code expressions used to access any binding available to a component. */
@PerComponentImplementation
public final class ComponentRequestRepresentations {
  // TODO(dpb,ronshapiro): refactor this and ComponentRequirementExpressions into a
  // HierarchicalComponentMap<K, V>, or perhaps this use a flattened ImmutableMap, built from its
  // parents? If so, maybe make BindingExpression.Factory create it.

  private final Optional<ComponentRequestRepresentations> parent;
  private final BindingGraph graph;
  private final ComponentImplementation componentImplementation;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final ProvisionBindingRepresentation.Factory provisionBindingRepresentationFactory;
  private final DaggerTypes types;
  private final Map<Binding, BindingRepresentation> representations = new HashMap<>();

  @Inject
  ComponentRequestRepresentations(
      @ParentComponent Optional<ComponentRequestRepresentations> parent,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      ComponentRequirementExpressions componentRequirementExpressions,
      ProvisionBindingRepresentation.Factory provisionBindingRepresentationFactory,
      DaggerTypes types) {
    this.parent = parent;
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.componentRequirementExpressions = requireNonNull(componentRequirementExpressions);
    this.provisionBindingRepresentationFactory = provisionBindingRepresentationFactory;
    this.types = types;
  }

  /**
   * Returns an expression that evaluates to the value of a binding request for a binding owned by
   * this component or an ancestor.
   *
   * @param requestingClass the class that will contain the expression
   * @throws IllegalStateException if there is no binding expression that satisfies the request
   */
  public Expression getDependencyExpression(BindingRequest request, ClassName requestingClass) {
    return getRequestRepresentation(request).getDependencyExpression(requestingClass);
  }

  /**
   * Equivalent to {@link #getDependencyExpression(BindingRequest, ClassName)} that is used only
   * when the request is for implementation of a component method.
   *
   * @throws IllegalStateException if there is no binding expression that satisfies the request
   */
  Expression getDependencyExpressionForComponentMethod(
      BindingRequest request,
      ComponentMethodDescriptor componentMethod,
      ComponentImplementation componentImplementation) {
    return getRequestRepresentation(request)
        .getDependencyExpressionForComponentMethod(componentMethod, componentImplementation);
  }

  /**
   * Returns the {@link CodeBlock} for the method arguments used with the factory {@code create()}
   * method for the given {@link ContributionBinding binding}.
   */
  CodeBlock getCreateMethodArgumentsCodeBlock(
      ContributionBinding binding, ClassName requestingClass) {
    return makeParametersCodeBlock(getCreateMethodArgumentsCodeBlocks(binding, requestingClass));
  }

  private List<CodeBlock> getCreateMethodArgumentsCodeBlocks(
      ContributionBinding binding, ClassName requestingClass) {
    List<CodeBlock> arguments = new ArrayList<>();

    if (binding.requiresModuleInstance()) {
      arguments.add(
          componentRequirementExpressions.getExpressionDuringInitialization(
              ComponentRequirement.forModule(binding.contributingModule().get().getType()),
              requestingClass));
    }

    binding.dependencies().stream()
        .map(dependency -> frameworkRequest(binding, dependency))
        .map(request -> getDependencyExpression(request, requestingClass))
        .map(Expression::codeBlock)
        .forEach(arguments::add);

    return arguments;
  }

  private static BindingRequest frameworkRequest(
      ContributionBinding binding, DependencyRequest dependency) {
    // TODO(bcorso): See if we can get rid of FrameworkTypeMatcher
    FrameworkType frameworkType =
        FrameworkTypeMapper.forBindingType()
            .getFrameworkType();
    return BindingRequest.bindingRequest(dependency.key(), frameworkType);
  }

  /**
   * Returns an expression that evaluates to the value of a dependency request, for passing to a
   * binding method, an {@code @Inject}-annotated constructor or member, or a proxy for one.
   *
   * <p>If the method is a generated static {@link InjectionMethods injection method}, each
   * parameter will be {@link Object} if the dependency's raw type is inaccessible. If that is the
   * case for this dependency, the returned expression will use a cast to evaluate to the raw type.
   *
   * @param requestingClass the class that will contain the expression
   */
  Expression getDependencyArgumentExpression(
      DependencyRequest dependencyRequest, ClassName requestingClass) {

    TypeMirror dependencyType = dependencyRequest.key().type().java();
    BindingRequest bindingRequest = bindingRequest(dependencyRequest);
    Expression dependencyExpression = getDependencyExpression(bindingRequest, requestingClass);

    if (dependencyRequest.kind().equals(RequestKind.INSTANCE)
        && !isTypeAccessibleFrom(dependencyType, requestingClass.packageName())
        && isRawTypeAccessible(dependencyType, requestingClass.packageName())) {
      return dependencyExpression.castTo(types.erasure(dependencyType));
    }

    return dependencyExpression;
  }

  /** Returns the implementation of a component method. */
  public MethodSpec getComponentMethod(ComponentMethodDescriptor componentMethod) {
    Preconditions.checkArgument(componentMethod.dependencyRequest().isPresent());
    BindingRequest request = bindingRequest(componentMethod.dependencyRequest().get());
    return MethodSpec.overriding(
            componentMethod.methodElement(),
            MoreTypes.asDeclared(graph.componentTypeElement().asType()),
            types)
        .addCode(
            getRequestRepresentation(request)
                .getComponentMethodImplementation(componentMethod, componentImplementation))
        .build();
  }

  /** Returns the {@link RequestRepresentation} for the given {@link BindingRequest}. */
  RequestRepresentation getRequestRepresentation(BindingRequest request) {
    Optional<Binding> localBinding = graph.localContributionBinding(request.key());

    if (localBinding.isPresent()) {
      return getBindingRepresentation(localBinding.get()).getRequestRepresentation(request);
    }

    Preconditions.checkArgument(parent.isPresent(), "no expression found for %s", request);
    return parent.get().getRequestRepresentation(request);
  }

  BindingRepresentation getBindingRepresentation(Binding binding) {
    return reentrantComputeIfAbsent(
        representations, binding, this::getBindingRepresentationUncached);
  }

  private BindingRepresentation getBindingRepresentationUncached(Binding binding) {
    return provisionBindingRepresentationFactory.create((ProvisionBinding) binding);
  }
}
