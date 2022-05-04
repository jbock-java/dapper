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

import static dagger.internal.codegen.base.Preconditions.checkArgument;
import static dagger.internal.codegen.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.base.Preconditions.checkState;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.erasure;
import static dagger.internal.codegen.xprocessing.XType.isVoid;
import static dagger.internal.codegen.xprocessing.XTypes.isAssignableTo;

import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.FrameworkType;
import dagger.internal.codegen.binding.FrameworkTypeMapper;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.collect.ImmutableList;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.xprocessing.MethodSpecs;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XType;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.RequestKind;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.MethodSpec;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** A central repository of code expressions used to access any binding available to a component. */
@PerComponentImplementation
public final class ComponentRequestRepresentations {
  // TODO(dpb,ronshapiro): refactor this and ComponentRequirementExpressions into a
  // HierarchicalComponentMap<K, V>, or perhaps this use a flattened ImmutableMap, built from its
  // parents? If so, maybe make RequestRepresentation.Factory create it.

  private final Optional<ComponentRequestRepresentations> parent;
  private final BindingGraph graph;
  private final ComponentImplementation componentImplementation;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final MembersInjectionBindingRepresentation.Factory
      membersInjectionBindingRepresentationFactory;
  private final ProvisionBindingRepresentation.Factory provisionBindingRepresentationFactory;
  private final ExperimentalSwitchingProviderDependencyRepresentation.Factory
      experimentalSwitchingProviderDependencyRepresentationFactory;
  private final XProcessingEnv processingEnv;
  private final Map<Binding, BindingRepresentation> representations = new HashMap<>();
  private final Map<Binding, ExperimentalSwitchingProviderDependencyRepresentation>
      experimentalSwitchingProviderDependencyRepresentations = new HashMap<>();

  @Inject
  ComponentRequestRepresentations(
      @ParentComponent Optional<ComponentRequestRepresentations> parent,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      ComponentRequirementExpressions componentRequirementExpressions,
      MembersInjectionBindingRepresentation.Factory membersInjectionBindingRepresentationFactory,
      ProvisionBindingRepresentation.Factory provisionBindingRepresentationFactory,
      ExperimentalSwitchingProviderDependencyRepresentation.Factory
          experimentalSwitchingProviderDependencyRepresentationFactory,
      XProcessingEnv processingEnv) {
    this.parent = parent;
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.membersInjectionBindingRepresentationFactory =
        membersInjectionBindingRepresentationFactory;
    this.provisionBindingRepresentationFactory = provisionBindingRepresentationFactory;
    this.experimentalSwitchingProviderDependencyRepresentationFactory =
        experimentalSwitchingProviderDependencyRepresentationFactory;
    this.componentRequirementExpressions = checkNotNull(componentRequirementExpressions);
    this.processingEnv = processingEnv;
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
   * Equivalent to {@code #getDependencyExpression(BindingRequest, ClassName)} that is used only
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
   * Returns the {@code CodeBlock} for the method arguments used with the factory {@code create()}
   * method for the given {@code ContributionBinding binding}.
   */
  CodeBlock getCreateMethodArgumentsCodeBlock(
      ContributionBinding binding, ClassName requestingClass) {
    return makeParametersCodeBlock(getCreateMethodArgumentsCodeBlocks(binding, requestingClass));
  }

  private ImmutableList<CodeBlock> getCreateMethodArgumentsCodeBlocks(
      ContributionBinding binding, ClassName requestingClass) {
    ImmutableList.Builder<CodeBlock> arguments = ImmutableList.builder();

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

    return arguments.build();
  }

  private static BindingRequest frameworkRequest(
      ContributionBinding binding, DependencyRequest dependency) {
    // TODO(bcorso): See if we can get rid of FrameworkTypeMatcher
    FrameworkType frameworkType =
        FrameworkTypeMapper.forBindingType(binding.bindingType())
            .getFrameworkType(dependency.kind());
    return BindingRequest.bindingRequest(dependency.key(), frameworkType);
  }

  /**
   * Returns an expression that evaluates to the value of a dependency request, for passing to a
   * binding method, an {@code @Inject}-annotated constructor or member, or a proxy for one.
   *
   * <p>If the method is a generated static {@code InjectionMethods injection method}, each
   * parameter will be {@code Object} if the dependency's raw type is inaccessible. If that is the
   * case for this dependency, the returned expression will use a cast to evaluate to the raw type.
   *
   * @param requestingClass the class that will contain the expression
   */
  Expression getDependencyArgumentExpression(
      DependencyRequest dependencyRequest, ClassName requestingClass) {

    XType dependencyType = dependencyRequest.key().type().xprocessing();
    BindingRequest bindingRequest = bindingRequest(dependencyRequest);
    Expression dependencyExpression = getDependencyExpression(bindingRequest, requestingClass);

    if (dependencyRequest.kind().equals(RequestKind.INSTANCE)
        && !isTypeAccessibleFrom(dependencyType, requestingClass.packageName())
        && isRawTypeAccessible(dependencyType, requestingClass.packageName())) {
      return dependencyExpression.castTo(erasure(dependencyType, processingEnv));
    }

    return dependencyExpression;
  }

  /** Returns the implementation of a component method. */
  public MethodSpec getComponentMethod(ComponentMethodDescriptor componentMethod) {
    checkArgument(componentMethod.dependencyRequest().isPresent());
    BindingRequest request = bindingRequest(componentMethod.dependencyRequest().get());
    return MethodSpecs.overriding(
            componentMethod.methodElement(), graph.componentTypeElement().getType())
        .addCode(
            request.isRequestKind(RequestKind.MEMBERS_INJECTION)
                ? getMembersInjectionComponentMethodImplementation(request, componentMethod)
                : getContributionComponentMethodImplementation(request, componentMethod))
        .build();
  }

  private CodeBlock getMembersInjectionComponentMethodImplementation(
      BindingRequest request, ComponentMethodDescriptor componentMethod) {
    checkArgument(request.isRequestKind(RequestKind.MEMBERS_INJECTION));
    XMethodElement methodElement = componentMethod.methodElement();
    RequestRepresentation requestRepresentation = getRequestRepresentation(request);
    MembersInjectionBinding binding =
        ((MembersInjectionRequestRepresentation) requestRepresentation).binding();
    if (binding.injectionSites().isEmpty()) {
      // If there are no injection sites either do nothing (if the return type is void) or return
      // the input instance as-is.
      return isVoid(methodElement.getReturnType())
          ? CodeBlock.of("")
          : CodeBlock.of(
              "return $L;", getSimpleName(getOnlyElement(methodElement.getParameters())));
    }
    Expression expression = getComponentMethodExpression(requestRepresentation, componentMethod);
    return isVoid(methodElement.getReturnType())
        ? CodeBlock.of("$L;", expression.codeBlock())
        : CodeBlock.of("return $L;", expression.codeBlock());
  }

  private CodeBlock getContributionComponentMethodImplementation(
      BindingRequest request, ComponentMethodDescriptor componentMethod) {
    checkArgument(!request.isRequestKind(RequestKind.MEMBERS_INJECTION));
    Expression expression =
        getComponentMethodExpression(getRequestRepresentation(request), componentMethod);
    return CodeBlock.of("return $L;", expression.codeBlock());
  }

  private Expression getComponentMethodExpression(
      RequestRepresentation requestRepresentation, ComponentMethodDescriptor componentMethod) {
    Expression expression =
        requestRepresentation.getDependencyExpressionForComponentMethod(
            componentMethod, componentImplementation);

    // Cast if the expression type does not match the component method's return type. This is useful
    // for types that have protected accessibility to the component but are not accessible to other
    // classes, e.g. shards, that may need to handle the implementation of the binding.
    XType returnType = componentMethod.methodElement().getReturnType();
    return !isVoid(returnType) && !isAssignableTo(expression.type(), returnType)
        ? expression.castTo(returnType)
        : expression;
  }

  /** Returns the {@code RequestRepresentation} for the given {@code BindingRequest}. */
  RequestRepresentation getRequestRepresentation(BindingRequest request) {
    Optional<Binding> localBinding =
        request.isRequestKind(RequestKind.MEMBERS_INJECTION)
            ? graph.localMembersInjectionBinding(request.key())
            : graph.localContributionBinding(request.key());

    if (localBinding.isPresent()) {
      return getBindingRepresentation(localBinding.get()).getRequestRepresentation(request);
    }

    checkArgument(parent.isPresent(), "no expression found for %s", request);
    return parent.get().getRequestRepresentation(request);
  }

  private BindingRepresentation getBindingRepresentation(Binding binding) {
    return reentrantComputeIfAbsent(
        representations, binding, this::getBindingRepresentationUncached);
  }

  private BindingRepresentation getBindingRepresentationUncached(Binding binding) {
    switch (binding.bindingType()) {
      case MEMBERS_INJECTION:
        return membersInjectionBindingRepresentationFactory.create(
            (MembersInjectionBinding) binding);
      case PROVISION:
        return provisionBindingRepresentationFactory.create((ProvisionBinding) binding);
    }
    throw new AssertionError();
  }

  /**
   * Returns an {@code ExperimentalSwitchingProviderDependencyRepresentation} for the requested
   * binding to satisfy dependency requests on it from experimental switching providers. Cannot be
   * used for Members Injection requests.
   */
  ExperimentalSwitchingProviderDependencyRepresentation
      getExperimentalSwitchingProviderDependencyRepresentation(BindingRequest request) {
    checkState(
        componentImplementation.compilerMode().isExperimentalMergedMode(),
        "Compiler mode should be experimentalMergedMode!");
    Optional<Binding> localBinding = graph.localContributionBinding(request.key());

    if (localBinding.isPresent()) {
      return reentrantComputeIfAbsent(
          experimentalSwitchingProviderDependencyRepresentations,
          localBinding.get(),
          binding ->
              experimentalSwitchingProviderDependencyRepresentationFactory.create(
                  (ProvisionBinding) binding));
    }

    checkArgument(parent.isPresent(), "no expression found for %s", request);
    return parent.get().getExperimentalSwitchingProviderDependencyRepresentation(request);
  }
}
