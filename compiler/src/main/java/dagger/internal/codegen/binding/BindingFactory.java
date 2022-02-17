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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.base.Scopes.uniqueScopeOf;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.xprocessing.XConverters.toXProcessing;
import static dagger.internal.codegen.xprocessing.XElement.isMethod;
import static dagger.internal.codegen.xprocessing.XElement.isVariableElement;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.spi.model.BindingKind.ASSISTED_FACTORY;
import static dagger.spi.model.BindingKind.ASSISTED_INJECTION;
import static dagger.spi.model.BindingKind.BOUND_INSTANCE;
import static dagger.spi.model.BindingKind.COMPONENT;
import static dagger.spi.model.BindingKind.COMPONENT_DEPENDENCY;
import static dagger.spi.model.BindingKind.COMPONENT_PROVISION;
import static dagger.spi.model.BindingKind.DELEGATE;
import static dagger.spi.model.BindingKind.INJECTION;
import static dagger.spi.model.BindingKind.PROVISION;
import static dagger.spi.model.BindingKind.SUBCOMPONENT_CREATOR;
import static io.jbock.auto.common.MoreTypes.asDeclared;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.ElementKind.METHOD;

import dagger.Module;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.xprocessing.XConstructorElement;
import dagger.internal.codegen.xprocessing.XConstructorType;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XExecutableParameterElement;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XMethodType;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.spi.model.BindingKind;
import dagger.spi.model.DaggerType;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Key;
import dagger.spi.model.RequestKind;
import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import jakarta.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;

/** A factory for {@link Binding} objects. */
public final class BindingFactory {
  private final XProcessingEnv processingEnv;
  private final DaggerTypes types;
  private final KeyFactory keyFactory;
  private final DependencyRequestFactory dependencyRequestFactory;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  BindingFactory(
      XProcessingEnv processingEnv,
      DaggerTypes types,
      KeyFactory keyFactory,
      DependencyRequestFactory dependencyRequestFactory,
      InjectionAnnotations injectionAnnotations) {
    this.processingEnv = processingEnv;
    this.types = types;
    this.keyFactory = keyFactory;
    this.dependencyRequestFactory = dependencyRequestFactory;
    this.injectionAnnotations = injectionAnnotations;
  }

  /**
   * Returns an {@link dagger.spi.model.BindingKind#INJECTION} binding.
   *
   * @param constructorElement the {@code @Inject}-annotated constructor
   * @param resolvedEnclosingType the parameterized type if the constructor is for a generic class and the
   *     binding should be for the parameterized type
   */
  // TODO(dpb): See if we can just pass the parameterized type and not also the constructor.
  public ProvisionBinding injectionBinding(
      XConstructorElement constructorElement, Optional<XType> resolvedEnclosingType) {
    Preconditions.checkArgument(
        constructorElement.hasAnnotation(TypeNames.INJECT)
            || constructorElement.hasAnnotation(TypeNames.ASSISTED_INJECT));
    Preconditions.checkArgument(injectionAnnotations.getQualifier(constructorElement).isEmpty());

    XConstructorType constructorType = constructorElement.getExecutableType();
    XType enclosingType = constructorElement.getEnclosingElement().getType();
    // If the class this is constructing has some type arguments, resolve everything.
    if (!enclosingType.getTypeArguments().isEmpty() && resolvedEnclosingType.isPresent()) {
      checkIsSameErasedType(resolvedEnclosingType.get(), enclosingType);
      enclosingType = resolvedEnclosingType.get();
      constructorType = constructorElement.asMemberOf(enclosingType);
    }

    // Collect all dependency requests within the provision method.
    // Note: we filter out @Assisted parameters since these aren't considered dependency requests.
    Set<DependencyRequest> provisionDependencies = new LinkedHashSet<>();
    for (int i = 0; i < constructorElement.getParameters().size(); i++) {
      XExecutableParameterElement parameter = constructorElement.getParameters().get(i);
      XType parameterType = constructorType.getParameterTypes().get(i);
      if (!AssistedInjectionAnnotations.isAssistedParameter(parameter)) {
        provisionDependencies.add(
            dependencyRequestFactory.forRequiredResolvedVariable(parameter, parameterType));
      }
    }

    ProvisionBinding.Builder builder =
        ProvisionBinding.builder()
            .bindingElement(constructorElement)
            .key(keyFactory.forInjectConstructorWithResolvedType(enclosingType))
            .provisionDependencies(provisionDependencies)
            .kind(
                constructorElement.hasAnnotation(TypeNames.ASSISTED_INJECT)
                    ? ASSISTED_INJECTION
                    : INJECTION)
            .scope(uniqueScopeOf(constructorElement.getEnclosingElement()));

    if (hasNonDefaultTypeParameters(enclosingType)) {
      builder.unresolved(injectionBinding(constructorElement, Optional.empty()));
    }
    return builder.build();
  }

  public ProvisionBinding assistedFactoryBinding(
      XTypeElement factory, Optional<XType> resolvedFactoryType) {

    // If the class this is constructing has some type arguments, resolve everything.
    XType factoryType = factory.getType();
    if (!factoryType.getTypeArguments().isEmpty() && resolvedFactoryType.isPresent()) {
      checkIsSameErasedType(resolvedFactoryType.get(), factoryType);
      factoryType = resolvedFactoryType.get();
    }

    XMethodElement factoryMethod = AssistedInjectionAnnotations.assistedFactoryMethod(factory);
    XMethodType factoryMethodType = factoryMethod.asMemberOf(factoryType);
    return ProvisionBinding.builder()
        .key(Key.builder(DaggerType.from(factoryType)).build())
        .bindingElement(factory)
        .provisionDependencies(
            Set.of(
                DependencyRequest.builder()
                    .key(Key.builder(DaggerType.from(factoryMethodType.getReturnType())).build())
                    .kind(RequestKind.PROVIDER)
                    .build()))
        .kind(ASSISTED_FACTORY)
        .build();
  }

  /**
   * Returns a {@link BindingKind#PROVISION} binding for a
   * {@code @Provides}-annotated method.
   *
   * @param contributedBy the installed module that declares or inherits the method
   */
  public ProvisionBinding providesMethodBinding(
      XMethodElement providesMethod, XTypeElement contributedBy) {
    return providesMethodBinding(providesMethod.toJavac(), contributedBy.toJavac());
  }

  /**
   * Returns a {@link BindingKind#PROVISION} binding for a {@code @Provides}-annotated
   * method.
   *
   * @param contributedBy the installed module that declares or inherits the method
   */
  public ProvisionBinding providesMethodBinding(
      ExecutableElement providesMethod, TypeElement contributedBy) {
    return setMethodBindingProperties(
        ProvisionBinding.builder(),
        providesMethod,
        contributedBy,
        keyFactory.forProvidesMethod(providesMethod, contributedBy),
        this::providesMethodBinding)
        .kind(PROVISION)
        .scope(uniqueScopeOf(toXProcessing(providesMethod, processingEnv)))
        .build();
  }

  private ProvisionBinding.Builder setMethodBindingProperties(
      ProvisionBinding.Builder builder,
      ExecutableElement method,
      TypeElement contributedBy,
      Key key,
      BiFunction<ExecutableElement, TypeElement, ProvisionBinding> create) {
    Preconditions.checkArgument(method.getKind().equals(METHOD));
    ExecutableType methodType =
        MoreTypes.asExecutable(
            types.asMemberOf(MoreTypes.asDeclared(contributedBy.asType()), method));
    if (!types.isSameType(methodType, method.asType())) {
      builder.unresolved(create.apply(method, MoreElements.asType(method.getEnclosingElement())));
    }
    return builder
        .bindingElement(toXProcessing(method, processingEnv))
        .contributingModule(contributedBy)
        .key(key)
        .dependencies(
            dependencyRequestFactory.forRequiredResolvedVariables(
                method.getParameters(), methodType.getParameterTypes()));
  }

  /** Returns a {@link BindingKind#COMPONENT} binding for the component. */
  public ProvisionBinding componentBinding(XTypeElement componentDefinitionType) {
    requireNonNull(componentDefinitionType);
    return ProvisionBinding.builder()
        .bindingElement(componentDefinitionType)
        .key(keyFactory.forType(componentDefinitionType.getType().toJavac()))
        .kind(COMPONENT)
        .build();
  }

  /**
   * Returns a {@link BindingKind#COMPONENT_DEPENDENCY} binding for a component's
   * dependency.
   */
  public ProvisionBinding componentDependencyBinding(ComponentRequirement dependency) {
    requireNonNull(dependency);
    return ProvisionBinding.builder()
        .bindingElement(toXProcessing(dependency.typeElement(), processingEnv))
        .key(keyFactory.forType(dependency.type()))
        .kind(COMPONENT_DEPENDENCY)
        .build();
  }

  /**
   * Returns a {@link BindingKind#COMPONENT_PROVISION}
   * binding for a method on a component's dependency.
   */
  public ProvisionBinding componentDependencyMethodBinding(
      ExecutableElement dependencyMethod) {
    Preconditions.checkArgument(dependencyMethod.getKind().equals(METHOD));
    Preconditions.checkArgument(dependencyMethod.getParameters().isEmpty());
    ProvisionBinding.Builder builder = ProvisionBinding.builder()
        .key(keyFactory.forComponentMethod(dependencyMethod))
        .kind(COMPONENT_PROVISION)
        .scope(uniqueScopeOf(toXProcessing(dependencyMethod, processingEnv)));
    return builder
        .bindingElement(toXProcessing(dependencyMethod, processingEnv))
        .build();
  }

  /**
   * Returns a {@link BindingKind#BOUND_INSTANCE} binding for a
   * {@code @BindsInstance}-annotated builder setter method or factory method parameter.
   */
  ProvisionBinding boundInstanceBinding(ComponentRequirement requirement, XElement element) {
    Preconditions.checkArgument(isVariableElement(element) || isMethod(element));
    return ProvisionBinding.builder()
        .bindingElement(element)
        .key(requirement.key().orElseThrow())
        .kind(BOUND_INSTANCE)
        .build();
  }

  /**
   * Returns a {@link BindingKind#SUBCOMPONENT_CREATOR} binding declared by a component
   * method that returns a subcomponent builder. Use {{@link
   * #subcomponentCreatorBinding(Set)}} for bindings declared using {@link
   * Module#subcomponents()}.
   *
   * @param component the component that declares or inherits the method
   */
  ProvisionBinding subcomponentCreatorBinding(
      ExecutableElement subcomponentCreatorMethod, TypeElement component) {
    Preconditions.checkArgument(subcomponentCreatorMethod.getKind().equals(METHOD));
    Preconditions.checkArgument(subcomponentCreatorMethod.getParameters().isEmpty());
    Key key =
        keyFactory.forSubcomponentCreatorMethod(
            subcomponentCreatorMethod, asDeclared(component.asType()));
    return ProvisionBinding.builder()
        .bindingElement(toXProcessing(subcomponentCreatorMethod, processingEnv))
        .key(key)
        .kind(SUBCOMPONENT_CREATOR)
        .build();
  }

  /**
   * Returns a {@link BindingKind#SUBCOMPONENT_CREATOR} binding declared using {@link
   * Module#subcomponents()}.
   */
  ProvisionBinding subcomponentCreatorBinding(
      Set<SubcomponentDeclaration> subcomponentDeclarations) {
    SubcomponentDeclaration subcomponentDeclaration = subcomponentDeclarations.iterator().next();
    return ProvisionBinding.builder()
        .key(subcomponentDeclaration.key())
        .kind(SUBCOMPONENT_CREATOR)
        .build();
  }

  /**
   * Returns a {@link BindingKind#DELEGATE} binding.
   *
   * @param delegateDeclaration the {@code @Binds}-annotated declaration
   */
  ContributionBinding delegateBinding(
      DelegateDeclaration delegateDeclaration) {
    return buildDelegateBinding(
        ProvisionBinding.builder()
            .scope(uniqueScopeOf(delegateDeclaration.bindingElement().get())),
        delegateDeclaration);
  }

  /**
   * Returns a {@link BindingKind#DELEGATE} binding used when there is no binding that
   * satisfies the {@code @Binds} declaration.
   */
  public ContributionBinding unresolvedDelegateBinding(DelegateDeclaration delegateDeclaration) {
    return buildDelegateBinding(
        ProvisionBinding.builder().scope(uniqueScopeOf(delegateDeclaration.bindingElement().get())),
        delegateDeclaration);
  }

  private ContributionBinding buildDelegateBinding(
      ProvisionBinding.Builder builder,
      DelegateDeclaration delegateDeclaration) {
    return builder
        .bindingElement(delegateDeclaration.bindingElement().orElseThrow())
        .contributingModule(delegateDeclaration.contributingModule().orElseThrow())
        .key(delegateDeclaration.key())
        .dependencies(delegateDeclaration.delegateRequest())
        .kind(DELEGATE)
        .build();
  }

  private void checkIsSameErasedType(XType type1, XType type2) {
    Preconditions.checkState(
        types.isSameType(types.erasure(toJavac(type1)), types.erasure(toJavac(type2))),
        "erased expected type: %s, erased actual type: %s",
        types.erasure(toJavac(type1)),
        types.erasure(toJavac(type2)));
  }

  private static boolean hasNonDefaultTypeParameters(XType type) {
    // If the type is not declared, then it can't have type parameters.
    if (!isDeclared(type)) {
      return false;
    }

    // If the element has no type parameters, none can be non-default.
    XType defaultType = type.getTypeElement().getType();
    if (defaultType.getTypeArguments().isEmpty()) {
      return false;
    }


    // The actual type parameter size can be different if the user is using a raw type.
    if (defaultType.getTypeArguments().size() != type.getTypeArguments().size()) {
      return true;
    }

    for (int i = 0; i < defaultType.getTypeArguments().size(); i++) {
      if (!defaultType.getTypeArguments().get(i).isSameType(type.getTypeArguments().get(i))) {
        return true;
      }
    }
    return false;
  }
}
