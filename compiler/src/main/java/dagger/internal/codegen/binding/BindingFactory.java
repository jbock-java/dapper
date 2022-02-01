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
import static dagger.internal.codegen.binding.Binding.hasNonDefaultTypeParameters;
import static dagger.model.BindingKind.ASSISTED_FACTORY;
import static dagger.model.BindingKind.ASSISTED_INJECTION;
import static dagger.model.BindingKind.BOUND_INSTANCE;
import static dagger.model.BindingKind.COMPONENT;
import static dagger.model.BindingKind.COMPONENT_DEPENDENCY;
import static dagger.model.BindingKind.COMPONENT_PROVISION;
import static dagger.model.BindingKind.DELEGATE;
import static dagger.model.BindingKind.INJECTION;
import static dagger.model.BindingKind.PROVISION;
import static dagger.model.BindingKind.SUBCOMPONENT_CREATOR;
import static dagger.spi.model.DaggerType.fromJava;
import static io.jbock.auto.common.MoreElements.isAnnotationPresent;
import static io.jbock.auto.common.MoreTypes.asDeclared;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.METHOD;

import dagger.Module;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.model.DependencyRequest;
import dagger.model.RequestKind;
import dagger.spi.model.Key;
import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import jakarta.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/** A factory for {@link Binding} objects. */
public final class BindingFactory {
  private final DaggerTypes types;
  private final KeyFactory keyFactory;
  private final DependencyRequestFactory dependencyRequestFactory;
  private final DaggerElements elements;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  BindingFactory(
      DaggerTypes types,
      DaggerElements elements,
      KeyFactory keyFactory,
      DependencyRequestFactory dependencyRequestFactory,
      InjectionAnnotations injectionAnnotations) {
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.dependencyRequestFactory = dependencyRequestFactory;
    this.injectionAnnotations = injectionAnnotations;
  }

  /**
   * Returns an {@link dagger.model.BindingKind#INJECTION} binding.
   *
   * @param constructorElement the {@code @Inject}-annotated constructor
   * @param resolvedType the parameterized type if the constructor is for a generic class and the
   *     binding should be for the parameterized type
   */
  // TODO(dpb): See if we can just pass the parameterized type and not also the constructor.
  public ProvisionBinding injectionBinding(
      ExecutableElement constructorElement, Optional<TypeMirror> resolvedType) {
    Preconditions.checkArgument(constructorElement.getKind().equals(CONSTRUCTOR));
    Preconditions.checkArgument(
        isAnnotationPresent(constructorElement, Inject.class)
            || isAnnotationPresent(constructorElement, AssistedInject.class));
    Preconditions.checkArgument(injectionAnnotations.getQualifier(constructorElement).isEmpty());

    ExecutableType constructorType = MoreTypes.asExecutable(constructorElement.asType());
    DeclaredType constructedType =
        MoreTypes.asDeclared(constructorElement.getEnclosingElement().asType());
    // If the class this is constructing has some type arguments, resolve everything.
    if (!constructedType.getTypeArguments().isEmpty() && resolvedType.isPresent()) {
      DeclaredType resolved = MoreTypes.asDeclared(resolvedType.get());
      // Validate that we're resolving from the correct type.
      Preconditions.checkState(
          types.isSameType(types.erasure(resolved), types.erasure(constructedType)),
          "erased expected type: %s, erased actual type: %s",
          types.erasure(resolved),
          types.erasure(constructedType));
      constructorType = MoreTypes.asExecutable(types.asMemberOf(resolved, constructorElement));
      constructedType = resolved;
    }

    // Collect all dependency requests within the provision method.
    // Note: we filter out @Assisted parameters since these aren't considered dependency requests.
    Set<DependencyRequest> provisionDependencies = new LinkedHashSet<>();
    for (int i = 0; i < constructorElement.getParameters().size(); i++) {
      VariableElement parameter = constructorElement.getParameters().get(i);
      TypeMirror parameterType = constructorType.getParameterTypes().get(i);
      if (!AssistedInjectionAnnotations.isAssistedParameter(parameter)) {
        provisionDependencies.add(
            dependencyRequestFactory.forRequiredResolvedVariable(parameter, parameterType));
      }
    }

    Key key = keyFactory.forInjectConstructorWithResolvedType(constructedType);
    ProvisionBinding.Builder builder =
        ProvisionBinding.builder()
            .bindingElement(constructorElement)
            .key(key)
            .provisionDependencies(provisionDependencies)
            .kind(
                isAnnotationPresent(constructorElement, AssistedInject.class)
                    ? ASSISTED_INJECTION
                    : INJECTION)
            .scope(uniqueScopeOf(constructorElement.getEnclosingElement()));

    TypeElement bindingTypeElement = MoreElements.asType(constructorElement.getEnclosingElement());
    if (hasNonDefaultTypeParameters(bindingTypeElement, key.type().java(), types)) {
      builder.unresolved(injectionBinding(constructorElement, Optional.empty()));
    }
    return builder.build();
  }

  public ProvisionBinding assistedFactoryBinding(
      TypeElement factory, Optional<TypeMirror> resolvedType) {

    // If the class this is constructing has some type arguments, resolve everything.
    DeclaredType factoryType = MoreTypes.asDeclared(factory.asType());
    if (!factoryType.getTypeArguments().isEmpty() && resolvedType.isPresent()) {
      DeclaredType resolved = MoreTypes.asDeclared(resolvedType.get());
      // Validate that we're resolving from the correct type by checking that the erasure of the
      // resolvedType is the same as the erasure of the factoryType.
      Preconditions.checkState(
          types.isSameType(types.erasure(resolved), types.erasure(factoryType)),
          "erased expected type: %s, erased actual type: %s",
          types.erasure(resolved),
          types.erasure(factoryType));
      factoryType = resolved;
    }

    ExecutableElement factoryMethod =
        AssistedInjectionAnnotations.assistedFactoryMethod(factory, elements);
    ExecutableType factoryMethodType =
        MoreTypes.asExecutable(types.asMemberOf(factoryType, factoryMethod));
    return ProvisionBinding.builder()
        .key(Key.builder(fromJava(factoryType)).build())
        .bindingElement(factory)
        .provisionDependencies(
            Set.of(
                DependencyRequest.builder()
                    .key(Key.builder(fromJava(factoryMethodType.getReturnType())).build())
                    .kind(RequestKind.PROVIDER)
                    .build()))
        .kind(ASSISTED_FACTORY)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#PROVISION} binding for a {@code @Provides}-annotated
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
        .scope(uniqueScopeOf(providesMethod))
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
        .bindingElement(method)
        .contributingModule(contributedBy)
        .key(key)
        .dependencies(
            dependencyRequestFactory.forRequiredResolvedVariables(
                method.getParameters(), methodType.getParameterTypes()));
  }

  /** Returns a {@link dagger.model.BindingKind#COMPONENT} binding for the component. */
  public ProvisionBinding componentBinding(XTypeElement componentDefinitionType) {
    requireNonNull(componentDefinitionType);
    return ProvisionBinding.builder()
        .bindingElement(componentDefinitionType.toJavac())
        .key(keyFactory.forType(componentDefinitionType.getType()))
        .kind(COMPONENT)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#COMPONENT_DEPENDENCY} binding for a component's
   * dependency.
   */
  public ProvisionBinding componentDependencyBinding(ComponentRequirement dependency) {
    requireNonNull(dependency);
    return ProvisionBinding.builder()
        .bindingElement(dependency.typeElement())
        .key(keyFactory.forType(dependency.type()))
        .kind(COMPONENT_DEPENDENCY)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#COMPONENT_PROVISION}
   * binding for a method on a component's dependency.
   */
  public ProvisionBinding componentDependencyMethodBinding(
      ExecutableElement dependencyMethod) {
    Preconditions.checkArgument(dependencyMethod.getKind().equals(METHOD));
    Preconditions.checkArgument(dependencyMethod.getParameters().isEmpty());
    ProvisionBinding.Builder builder = ProvisionBinding.builder()
        .key(keyFactory.forComponentMethod(dependencyMethod))
        .kind(COMPONENT_PROVISION)
        .scope(uniqueScopeOf(dependencyMethod));
    return builder
        .bindingElement(dependencyMethod)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#BOUND_INSTANCE} binding for a
   * {@code @BindsInstance}-annotated builder setter method or factory method parameter.
   */
  ProvisionBinding boundInstanceBinding(ComponentRequirement requirement, Element element) {
    Preconditions.checkArgument(element instanceof VariableElement || element instanceof ExecutableElement);
    return ProvisionBinding.builder()
        .bindingElement(element)
        .key(requirement.key().orElseThrow())
        .kind(BOUND_INSTANCE)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#SUBCOMPONENT_CREATOR} binding declared by a component
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
        .bindingElement(subcomponentCreatorMethod)
        .key(key)
        .kind(SUBCOMPONENT_CREATOR)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#SUBCOMPONENT_CREATOR} binding declared using {@link
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
   * Returns a {@link dagger.model.BindingKind#DELEGATE} binding.
   *
   * @param delegateDeclaration the {@code @Binds}-annotated declaration
   */
  ContributionBinding delegateBinding(
      DelegateDeclaration delegateDeclaration) {
    return buildDelegateBinding(
        ProvisionBinding.builder()
            .scope(uniqueScopeOf(delegateDeclaration.bindingElement().orElseThrow())),
        delegateDeclaration);
  }

  /**
   * Returns a {@link dagger.model.BindingKind#DELEGATE} binding used when there is no binding that
   * satisfies the {@code @Binds} declaration.
   */
  public ContributionBinding unresolvedDelegateBinding(DelegateDeclaration delegateDeclaration) {
    return buildDelegateBinding(
        ProvisionBinding.builder().scope(uniqueScopeOf(delegateDeclaration.bindingElement().orElseThrow())),
        delegateDeclaration
    );
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
}
