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

import static dagger.internal.codegen.base.Util.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.xprocessing.XElements.asConstructor;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static io.jbock.auto.common.MoreElements.isAnnotationPresent;
import static java.util.Objects.requireNonNull;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XConstructorElement;
import dagger.internal.codegen.xprocessing.XConstructorType;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XHasModifiers;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XMethodType;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.internal.codegen.xprocessing.XVariableElement;
import dagger.spi.model.BindingKind;
import io.jbock.auto.common.Equivalence;
import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ParameterSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/** Assisted injection utility methods. */
public final class AssistedInjectionAnnotations {
  public static XMethodElement assistedFactoryMethod(XTypeElement factory) {
    return getOnlyElement(assistedFactoryMethods(factory));
  }

  /** Returns the list of abstract factory methods for the given factory {@link XTypeElement}. */
  public static Set<XMethodElement> assistedFactoryMethods(XTypeElement factory) {
    return factory.getAllNonPrivateInstanceMethods().stream()
        .filter(XHasModifiers::isAbstract)
        .filter(method -> !method.isJavaDefault())
        .collect(toImmutableSet());
  }

  /** Returns {@code true} if the element uses assisted injection. */
  public static boolean isAssistedInjectionType(XTypeElement typeElement) {
    return assistedInjectedConstructors(typeElement).stream()
        .anyMatch(constructor -> constructor.hasAnnotation(TypeNames.ASSISTED_INJECT));
  }

  /** Returns {@code true} if this binding is an assisted factory. */
  public static boolean isAssistedFactoryType(XElement element) {
    return element.hasAnnotation(TypeNames.ASSISTED_FACTORY);
  }

  /**
   * Returns the list of assisted parameters as {@link ParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static List<ParameterSpec> assistedParameterSpecs(
      Binding binding) {
    Preconditions.checkArgument(binding.kind() == BindingKind.ASSISTED_INJECTION);
    XConstructorElement constructor = asConstructor(binding.bindingElement().get());
    XConstructorType constructorType = constructor.asMemberOf(binding.key().type().xprocessing());
    return assistedParameterSpecs(constructor.getParameters(), constructorType.getParameterTypes());
  }

  private static List<ParameterSpec> assistedParameterSpecs(
      List<? extends XVariableElement> paramElements, List<XType> paramTypes) {
    List<ParameterSpec> assistedParameterSpecs = new ArrayList<>();
    for (int i = 0; i < paramElements.size(); i++) {
      XVariableElement paramElement = paramElements.get(i);
      XType paramType = paramTypes.get(i);
      if (isAssistedParameter(paramElement)) {
        assistedParameterSpecs.add(
            ParameterSpec.builder(paramType.getTypeName(), getSimpleName(paramElement)).build());
      }
    }
    return assistedParameterSpecs;
  }

  /**
   * Returns the list of assisted factory parameters as {@link ParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static List<ParameterSpec> assistedFactoryParameterSpecs(
      Binding binding) {
    Preconditions.checkArgument(binding.kind() == BindingKind.ASSISTED_FACTORY);

    XTypeElement factory = asTypeElement(binding.bindingElement().get());
    AssistedFactoryMetadata metadata = AssistedFactoryMetadata.create(factory.getType());
    XMethodType factoryMethodType =
        metadata.factoryMethod().asMemberOf(binding.key().type().xprocessing());
    return assistedParameterSpecs(
        // Use the order of the parameters from the @AssistedFactory method but use the parameter
        // names of the @AssistedInject constructor.
        metadata.assistedFactoryAssistedParameters().stream()
            .map(metadata.assistedInjectAssistedParametersMap()::get)
            .collect(toImmutableList()),
        factoryMethodType.getParameterTypes());
  }

  /** Returns the constructors in {@code type} that are annotated with {@link AssistedInject}. */
  public static Set<XConstructorElement> assistedInjectedConstructors(XTypeElement type) {
    return type.getConstructors().stream()
        .filter(constructor -> constructor.hasAnnotation(TypeNames.ASSISTED_INJECT))
        .collect(toImmutableSet());
  }

  public static List<XVariableElement> assistedParameters(Binding binding) {
    return binding.kind() == BindingKind.ASSISTED_INJECTION
        ? asConstructor(binding.bindingElement().get()).getParameters().stream()
        .filter(AssistedInjectionAnnotations::isAssistedParameter)
        .collect(toImmutableList())
        : List.of();
  }

  /** Returns {@code true} if this binding is uses assisted injection. */
  public static boolean isAssistedParameter(XVariableElement param) {
    return param.hasAnnotation(TypeNames.ASSISTED);
  }

  /** Returns {@code true} if this binding is uses assisted injection. */
  public static boolean isAssistedParameter(VariableElement param) {
    return isAnnotationPresent(MoreElements.asVariable(param), Assisted.class);
  }

  /** Metadata about an {@link dagger.assisted.AssistedFactory} annotated type. */
  public static final class AssistedFactoryMetadata {
    private final XTypeElement factory;
    private final XType factoryType;
    private final XMethodElement factoryMethod;
    private final XMethodType factoryMethodType;
    private final XTypeElement assistedInjectElement;
    private final XType assistedInjectType;
    private final List<AssistedInjectionAnnotations.AssistedParameter> assistedInjectAssistedParameters;
    private final List<AssistedInjectionAnnotations.AssistedParameter> assistedFactoryAssistedParameters;

    private AssistedFactoryMetadata(
        XTypeElement factory,
        XType factoryType,
        XMethodElement factoryMethod,
        XMethodType factoryMethodType,
        XTypeElement assistedInjectElement,
        XType assistedInjectType,
        List<AssistedParameter> assistedInjectAssistedParameters,
        List<AssistedParameter> assistedFactoryAssistedParameters) {
      this.factory = requireNonNull(factory);
      this.factoryType = requireNonNull(factoryType);
      this.factoryMethod = requireNonNull(factoryMethod);
      this.factoryMethodType = factoryMethodType;
      this.assistedInjectElement = requireNonNull(assistedInjectElement);
      this.assistedInjectType = requireNonNull(assistedInjectType);
      this.assistedInjectAssistedParameters = requireNonNull(assistedInjectAssistedParameters);
      this.assistedFactoryAssistedParameters = requireNonNull(assistedFactoryAssistedParameters);
    }

    public static AssistedFactoryMetadata create(XType factoryType) {
      XTypeElement factoryElement = factoryType.getTypeElement();
      XMethodElement factoryMethod = assistedFactoryMethod(factoryElement);
      XMethodType factoryMethodType = factoryMethod.asMemberOf(factoryType);
      XType assistedInjectType = factoryMethodType.getReturnType();
      XTypeElement assistedInjectElement = assistedInjectType.getTypeElement();
      return new AssistedFactoryMetadata(
          factoryElement,
          factoryType,
          factoryMethod,
          factoryMethodType,
          assistedInjectElement,
          assistedInjectType,
          AssistedInjectionAnnotations.assistedInjectAssistedParameters(assistedInjectType),
          AssistedInjectionAnnotations.assistedFactoryAssistedParameters(
              factoryMethod, factoryMethodType));
    }

    public XTypeElement factory() {
      return factory;
    }

    public XType factoryType() {
      return factoryType;
    }

    public XMethodElement factoryMethod() {
      return factoryMethod;
    }

    public XMethodType factoryMethodType() {
      return factoryMethodType;
    }

    public XTypeElement assistedInjectElement() {
      return assistedInjectElement;
    }

    public XType assistedInjectType() {
      return assistedInjectType;
    }

    public List<AssistedInjectionAnnotations.AssistedParameter> assistedInjectAssistedParameters() {
      return assistedInjectAssistedParameters;
    }

    public List<AssistedInjectionAnnotations.AssistedParameter> assistedFactoryAssistedParameters() {
      return assistedFactoryAssistedParameters;
    }

    private final Supplier<Map<AssistedParameter, XVariableElement>> assistedInjectAssistedParametersMapCache = Suppliers.memoize(() -> {
      Map<AssistedParameter, XVariableElement> builder = new LinkedHashMap<>();
      for (AssistedParameter assistedParameter : assistedInjectAssistedParameters()) {
        builder.put(assistedParameter, assistedParameter.element());
      }
      return builder;
    });

    public Map<AssistedParameter, XVariableElement> assistedInjectAssistedParametersMap() {
      return assistedInjectAssistedParametersMapCache.get();
    }

    private final Supplier<Map<AssistedParameter, XVariableElement>> assistedFactoryAssistedParametersMapCache = Suppliers.memoize(() -> {
      Map<AssistedParameter, XVariableElement> builder = new LinkedHashMap<>();
      for (AssistedParameter assistedParameter : assistedFactoryAssistedParameters()) {
        builder.put(assistedParameter, assistedParameter.element());
      }
      return builder;
    });

    public Map<AssistedParameter, XVariableElement> assistedFactoryAssistedParametersMap() {
      return assistedFactoryAssistedParametersMapCache.get();
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
    private final XVariableElement parameterElement;
    private final XType parameterType;

    private AssistedParameter(
        String qualifier,
        Equivalence.Wrapper<TypeMirror> wrappedType,
        XVariableElement parameterElement,
        XType parameterType) {
      this.qualifier = requireNonNull(qualifier);
      this.wrappedType = requireNonNull(wrappedType);
      this.parameterElement = parameterElement;
      this.parameterType = parameterType;
    }

    public static AssistedParameter create(
        XVariableElement parameter,
        XType parameterType) {
      return new AssistedParameter(
          Optional.ofNullable(parameter.getAnnotation(TypeNames.ASSISTED))
              .map(assisted -> assisted.getAsString("value"))
              .orElse(""),
          MoreTypes.equivalence().wrap(toJavac(parameterType)),
          parameter,
          parameterType);
    }

    /** Returns the string qualifier from the {@link Assisted#value()}. */
    public String qualifier() {
      return qualifier;
    }

    /** Returns the wrapper for the type annotated with {@link Assisted}. */
    Equivalence.Wrapper<TypeMirror> wrappedType() {
      return wrappedType;
    }

    /** Returns the type annotated with {@link Assisted}. */
    public XType type() {
      return parameterType;
    }

    public XVariableElement element() {
      return parameterElement;
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

  public static List<AssistedParameter> assistedInjectAssistedParameters(
      XType assistedInjectType) {
    // We keep track of the constructor both as an ExecutableElement to access @Assisted
    // parameters and as an ExecutableType to access the resolved parameter types.
    XConstructorElement assistedInjectConstructor =
        getOnlyElement(assistedInjectedConstructors(assistedInjectType.getTypeElement()));
    XConstructorType assistedInjectConstructorType =
        assistedInjectConstructor.asMemberOf(assistedInjectType);

    List<AssistedParameter> builder = new ArrayList<>();
    for (int i = 0; i < assistedInjectConstructor.getParameters().size(); i++) {
      XVariableElement parameter = assistedInjectConstructor.getParameters().get(i);
      XType parameterType = assistedInjectConstructorType.getParameterTypes().get(i);
      if (parameter.hasAnnotation(TypeNames.ASSISTED)) {
        builder.add(AssistedParameter.create(parameter, parameterType));
      }
    }
    return builder;
  }

  private static List<AssistedParameter> assistedFactoryAssistedParameters(
      XMethodElement factoryMethod, XMethodType factoryMethodType) {
    List<AssistedParameter> builder = new ArrayList<>();
    for (int i = 0; i < factoryMethod.getParameters().size(); i++) {
      XVariableElement parameter = factoryMethod.getParameters().get(i);
      XType parameterType = factoryMethodType.getParameterTypes().get(i);
      builder.add(AssistedParameter.create(parameter, parameterType));
    }
    return builder;
  }

  private AssistedInjectionAnnotations() {
  }
}
