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

package dagger.internal.codegen.binding;

import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.MoreAnnotationValues.getStringValue;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnnotationMirror;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import com.google.auto.common.Equivalence;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.BindingKind;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/** Assisted injection utility methods. */
public final class AssistedInjectionAnnotations {
  /** Returns the factory method for the given factory {@link TypeElement}. */
  public static ExecutableElement assistedFactoryMethod(
      TypeElement factory, DaggerElements elements) {
    return getOnlyElement(assistedFactoryMethods(factory, elements));
  }

  /** Returns the list of abstract factory methods for the given factory {@link TypeElement}. */
  public static Set<ExecutableElement> assistedFactoryMethods(
      TypeElement factory, DaggerElements elements) {
    return elements.getLocalAndInheritedMethods(factory).stream()
        .filter(method -> method.getModifiers().contains(ABSTRACT))
        .filter(method -> !method.isDefault())
        .collect(toImmutableSet());
  }

  /** Returns {@code true} if the element uses assisted injection. */
  public static boolean isAssistedInjectionType(TypeElement typeElement) {
    Set<ExecutableElement> injectConstructors = assistedInjectedConstructors(typeElement);
    return !injectConstructors.isEmpty()
        && isAnnotationPresent(getOnlyElement(injectConstructors), AssistedInject.class);
  }

  /** Returns {@code true} if this binding is an assisted factory. */
  public static boolean isAssistedFactoryType(Element element) {
    return isAnnotationPresent(element, AssistedFactory.class);
  }

  /**
   * Returns the list of assisted parameters as {@link ParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static ImmutableList<ParameterSpec> assistedParameterSpecs(
      Binding binding, DaggerTypes types) {
    checkArgument(binding.kind() == BindingKind.ASSISTED_INJECTION);
    ExecutableElement constructor = asExecutable(binding.bindingElement().get());
    ExecutableType constructorType =
        asExecutable(types.asMemberOf(asDeclared(binding.key().type()), constructor));
    return assistedParameterSpecs(constructor.getParameters(), constructorType.getParameterTypes());
  }

  private static ImmutableList<ParameterSpec> assistedParameterSpecs(
      List<? extends VariableElement> paramElements, List<? extends TypeMirror> paramTypes) {
    ImmutableList.Builder<ParameterSpec> assistedParameterSpecs = ImmutableList.builder();
    for (int i = 0; i < paramElements.size(); i++) {
      VariableElement paramElement = paramElements.get(i);
      TypeMirror paramType = paramTypes.get(i);
      if (isAssistedParameter(paramElement)) {
        assistedParameterSpecs.add(
            ParameterSpec.builder(TypeName.get(paramType), paramElement.getSimpleName().toString())
                .build());
      }
    }
    return assistedParameterSpecs.build();
  }

  /**
   * Returns the list of assisted factory parameters as {@link ParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static ImmutableList<ParameterSpec> assistedFactoryParameterSpecs(
      Binding binding, DaggerElements elements, DaggerTypes types) {
    checkArgument(binding.kind() == BindingKind.ASSISTED_FACTORY);

    AssistedFactoryMetadata metadata =
        AssistedFactoryMetadata.create(binding.bindingElement().get().asType(), elements, types);
    ExecutableType factoryMethodType =
        asExecutable(types.asMemberOf(asDeclared(binding.key().type()), metadata.factoryMethod()));
    return assistedParameterSpecs(
        // Use the order of the parameters from the @AssistedFactory method but use the parameter
        // names of the @AssistedInject constructor.
        metadata.assistedFactoryAssistedParameters().stream()
            .map(metadata.assistedInjectAssistedParametersMap()::get)
            .collect(toImmutableList()),
        factoryMethodType.getParameterTypes());
  }

  /** Returns the constructors in {@code type} that are annotated with {@link AssistedInject}. */
  public static Set<ExecutableElement> assistedInjectedConstructors(TypeElement type) {
    return constructorsIn(type.getEnclosedElements()).stream()
        .filter(constructor -> isAnnotationPresent(constructor, AssistedInject.class))
        .collect(toImmutableSet());
  }

  public static List<VariableElement> assistedParameters(Binding binding) {
    return binding.kind() == BindingKind.ASSISTED_INJECTION
        ? assistedParameters(asExecutable(binding.bindingElement().get()))
        : List.of();
  }

  private static List<VariableElement> assistedParameters(ExecutableElement constructor) {
    return constructor.getParameters().stream()
        .filter(AssistedInjectionAnnotations::isAssistedParameter)
        .collect(toImmutableList());
  }

  /** Returns {@code true} if this binding is uses assisted injection. */
  public static boolean isAssistedParameter(VariableElement param) {
    return isAnnotationPresent(MoreElements.asVariable(param), Assisted.class);
  }

  /** Metadata about an {@link dagger.assisted.AssistedFactory} annotated type. */
  public static final class AssistedFactoryMetadata {
    private final TypeElement factory;
    private final DeclaredType factoryType;
    private final ExecutableElement factoryMethod;
    private final TypeElement assistedInjectElement;
    private final DeclaredType assistedInjectType;
    private final ImmutableList<AssistedInjectionAnnotations.AssistedParameter> assistedInjectAssistedParameters;
    private final ImmutableList<AssistedInjectionAnnotations.AssistedParameter> assistedFactoryAssistedParameters;

    private AssistedFactoryMetadata(
        TypeElement factory,
        DeclaredType factoryType,
        ExecutableElement factoryMethod,
        TypeElement assistedInjectElement,
        DeclaredType assistedInjectType,
        ImmutableList<AssistedInjectionAnnotations.AssistedParameter> assistedInjectAssistedParameters,
        ImmutableList<AssistedInjectionAnnotations.AssistedParameter> assistedFactoryAssistedParameters) {
      this.factory = requireNonNull(factory);
      this.factoryType = requireNonNull(factoryType);
      this.factoryMethod = requireNonNull(factoryMethod);
      this.assistedInjectElement = requireNonNull(assistedInjectElement);
      this.assistedInjectType = requireNonNull(assistedInjectType);
      this.assistedInjectAssistedParameters = requireNonNull(assistedInjectAssistedParameters);
      this.assistedFactoryAssistedParameters = requireNonNull(assistedFactoryAssistedParameters);
    }

    public static AssistedFactoryMetadata create(
        TypeMirror factory, DaggerElements elements, DaggerTypes types) {
      DeclaredType factoryType = asDeclared(factory);
      TypeElement factoryElement = asTypeElement(factoryType);
      ExecutableElement factoryMethod = assistedFactoryMethod(factoryElement, elements);
      ExecutableType factoryMethodType = asExecutable(types.asMemberOf(factoryType, factoryMethod));
      DeclaredType assistedInjectType = asDeclared(factoryMethodType.getReturnType());
      return new AssistedFactoryMetadata(
          factoryElement,
          factoryType,
          factoryMethod,
          asTypeElement(assistedInjectType),
          assistedInjectType,
          AssistedInjectionAnnotations.assistedInjectAssistedParameters(assistedInjectType, types),
          AssistedInjectionAnnotations.assistedFactoryAssistedParameters(
              factoryMethod, factoryMethodType));
    }

    public TypeElement factory() {
      return factory;
    }

    public DeclaredType factoryType() {
      return factoryType;
    }

    public ExecutableElement factoryMethod() {
      return factoryMethod;
    }

    public TypeElement assistedInjectElement() {
      return assistedInjectElement;
    }

    public DeclaredType assistedInjectType() {
      return assistedInjectType;
    }

    public ImmutableList<AssistedInjectionAnnotations.AssistedParameter> assistedInjectAssistedParameters() {
      return assistedInjectAssistedParameters;
    }

    public ImmutableList<AssistedInjectionAnnotations.AssistedParameter> assistedFactoryAssistedParameters() {
      return assistedFactoryAssistedParameters;
    }

    public ImmutableMap<AssistedParameter, VariableElement> assistedInjectAssistedParametersMap() {
      ImmutableMap.Builder<AssistedParameter, VariableElement> builder = ImmutableMap.builder();
      for (AssistedParameter assistedParameter : assistedInjectAssistedParameters()) {
        builder.put(assistedParameter, assistedParameter.variableElement);
      }
      return builder.build();
    }

    public ImmutableMap<AssistedParameter, VariableElement> assistedFactoryAssistedParametersMap() {
      ImmutableMap.Builder<AssistedParameter, VariableElement> builder = ImmutableMap.builder();
      for (AssistedParameter assistedParameter : assistedFactoryAssistedParameters()) {
        builder.put(assistedParameter, assistedParameter.variableElement);
      }
      return builder.build();
    }
  }

  /**
   * Metadata about an {@link Assisted} annotated parameter.
   *
   * <p>This parameter can represent an {@link Assisted} annotated parameter from an {@link
   * AssistedInject} constructor or an {@link AssistedFactory} method.
   */
  public static final class AssistedParameter {

    private final String qualifier;
    private final Equivalence.Wrapper<TypeMirror> wrappedType;
    private final VariableElement variableElement;

    private AssistedParameter(
        String qualifier,
        Equivalence.Wrapper<TypeMirror> wrappedType,
        VariableElement variableElement) {
      this.qualifier = requireNonNull(qualifier);
      this.wrappedType = requireNonNull(wrappedType);
      this.variableElement = variableElement;
    }

    public static AssistedParameter create(VariableElement parameter, TypeMirror parameterType) {
      return new AssistedParameter(
          getAnnotationMirror(parameter, TypeNames.ASSISTED)
              .map(assisted -> getStringValue(assisted, "value"))
              .orElse(""),
          MoreTypes.equivalence().wrap(parameterType),
          parameter);
    }

    /** Returns the string qualifier from the {@link Assisted#value()}. */
    public String qualifier() {
      return qualifier;
    }

    /** Returns the wrapper for the type annotated with {@link Assisted}. */
    public Equivalence.Wrapper<TypeMirror> wrappedType() {
      return wrappedType;
    }

    /** Returns the type annotated with {@link Assisted}. */
    public TypeMirror type() {
      return wrappedType().get();
    }

    public VariableElement variableElement() {
      return variableElement;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      AssistedParameter that = (AssistedParameter) o;
      return qualifier.equals(that.qualifier) && wrappedType.equals(that.wrappedType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(qualifier, wrappedType);
    }

    @Override
    public String toString() {
      return qualifier().isEmpty()
          ? String.format("@Assisted %s", type())
          : String.format("@Assisted(\"%s\") %s", qualifier(), type());
    }
  }

  public static ImmutableList<AssistedParameter> assistedInjectAssistedParameters(
      DeclaredType assistedInjectType, DaggerTypes types) {
    // We keep track of the constructor both as an ExecutableElement to access @Assisted
    // parameters and as an ExecutableType to access the resolved parameter types.
    ExecutableElement assistedInjectConstructor =
        getOnlyElement(assistedInjectedConstructors(asTypeElement(assistedInjectType)));
    ExecutableType assistedInjectConstructorType =
        asExecutable(types.asMemberOf(assistedInjectType, assistedInjectConstructor));

    ImmutableList.Builder<AssistedParameter> builder = ImmutableList.builder();
    for (int i = 0; i < assistedInjectConstructor.getParameters().size(); i++) {
      VariableElement parameter = assistedInjectConstructor.getParameters().get(i);
      TypeMirror parameterType = assistedInjectConstructorType.getParameterTypes().get(i);
      if (isAnnotationPresent(parameter, Assisted.class)) {
        builder.add(AssistedParameter.create(parameter, parameterType));
      }
    }
    return builder.build();
  }

  public static ImmutableList<AssistedParameter> assistedFactoryAssistedParameters(
      ExecutableElement factoryMethod, ExecutableType factoryMethodType) {
    ImmutableList.Builder<AssistedParameter> builder = ImmutableList.builder();
    for (int i = 0; i < factoryMethod.getParameters().size(); i++) {
      VariableElement parameter = factoryMethod.getParameters().get(i);
      TypeMirror parameterType = factoryMethodType.getParameterTypes().get(i);
      builder.add(AssistedParameter.create(parameter, parameterType));
    }
    return builder.build();
  }

  private AssistedInjectionAnnotations() {
  }
}
