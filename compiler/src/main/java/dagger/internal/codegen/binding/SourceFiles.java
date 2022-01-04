/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.javapoet.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.javapoet.TypeNames.MAP_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.MAP_PROVIDER_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.PROVIDER_OF_LAZY;
import static dagger.internal.codegen.javapoet.TypeNames.SET_FACTORY;
import static dagger.model.BindingKind.ASSISTED_INJECTION;
import static dagger.model.BindingKind.INJECTION;
import static dagger.model.BindingKind.MULTIBOUND_MAP;
import static dagger.model.BindingKind.MULTIBOUND_SET;
import static javax.lang.model.SourceVersion.isName;

import com.google.auto.common.MoreElements;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.SetFactory;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Util;
import dagger.model.DependencyRequest;
import dagger.model.RequestKind;
import jakarta.inject.Provider;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;

/** Utilities for generating files. */
public class SourceFiles {

  /**
   * Generates names and keys for the factory class fields needed to hold the framework classes for
   * all of the dependencies of {@code binding}. It is responsible for choosing a name that
   *
   * <ul>
   *   <li>represents all of the dependency requests for this key
   *   <li>is <i>probably</i> associated with the type being bound
   *   <li>is unique within the class
   * </ul>
   *
   * @param binding must be an unresolved binding (type parameters must match its type element's)
   */
  public static Map<DependencyRequest, FrameworkField>
  generateBindingFieldsForDependencies(Binding binding) {
    Preconditions.checkArgument(binding.unresolved().isEmpty(), "binding must be unresolved: %s", binding);

    FrameworkTypeMapper frameworkTypeMapper =
        FrameworkTypeMapper.forBindingType(binding.bindingType());

    return Util.toMap(
        binding.dependencies(),
        dependency ->
            FrameworkField.create(
                ClassName.get(
                    frameworkTypeMapper.getFrameworkType(dependency.kind()).frameworkClass()),
                TypeName.get(dependency.key().type()),
                DependencyVariableNamer.name(dependency)));
  }

  public static CodeBlock frameworkTypeUsageStatement(
      CodeBlock frameworkTypeMemberSelect, RequestKind dependencyKind) {
    switch (dependencyKind) {
      case LAZY:
        return CodeBlock.of("$T.lazy($L)", DOUBLE_CHECK, frameworkTypeMemberSelect);
      case INSTANCE:
        return CodeBlock.of("$L.get()", frameworkTypeMemberSelect);
      case PROVIDER:
        return frameworkTypeMemberSelect;
      case PROVIDER_OF_LAZY:
        return CodeBlock.of("$T.create($L)", PROVIDER_OF_LAZY, frameworkTypeMemberSelect);
      default: // including PRODUCED
        throw new AssertionError(dependencyKind);
    }
  }

  /**
   * Returns a mapping of {@link DependencyRequest}s to {@link CodeBlock}s that {@linkplain
   * #frameworkTypeUsageStatement(CodeBlock, RequestKind) use them}.
   */
  public static Map<DependencyRequest, CodeBlock> frameworkFieldUsages(
      Set<DependencyRequest> dependencies,
      Map<DependencyRequest, FieldSpec> fields) {
    return Util.toMap(
        dependencies,
        dep -> frameworkTypeUsageStatement(CodeBlock.of("$N", fields.get(dep)), dep.kind()));
  }

  /** Returns the generated factory or members injector name for a binding. */
  public static ClassName generatedClassNameForBinding(Binding binding) {
    switch (binding.bindingType()) {
      case PROVISION:
        ContributionBinding contribution = (ContributionBinding) binding;
        switch (contribution.kind()) {
          case ASSISTED_INJECTION:
          case INJECTION:
          case PROVISION:
            return elementBasedClassName(
                MoreElements.asExecutable(binding.bindingElement().get()), "Factory");

          case ASSISTED_FACTORY:
            return siblingClassName(MoreElements.asType(binding.bindingElement().get()), "_Impl");

          default:
            throw new AssertionError();
        }

      case MEMBERS_INJECTION:
        return membersInjectorNameForType(
            ((MembersInjectionBinding) binding).membersInjectedType());
    }
    throw new AssertionError();
  }

  /**
   * Calculates an appropriate {@link ClassName} for a generated class that is based on {@code
   * element}, appending {@code suffix} at the end.
   *
   * <p>This will always return a {@linkplain ClassName#topLevelClassName() top level class name},
   * even if {@code element}'s enclosing class is a nested type.
   */
  public static ClassName elementBasedClassName(ExecutableElement element, String suffix) {
    ClassName enclosingClassName =
        ClassName.get(MoreElements.asType(element.getEnclosingElement()));
    String simpleName = element.getSimpleName().toString();
    String methodName =
        element.getKind().equals(ElementKind.CONSTRUCTOR)
            ? ""
            : Character.toUpperCase(simpleName.charAt(0)) + simpleName.substring(1);
    return ClassName.get(
        enclosingClassName.packageName(),
        classFileName(enclosingClassName) + "_" + methodName + suffix);
  }

  public static TypeName parameterizedGeneratedTypeNameForBinding(Binding binding) {
    ClassName className = generatedClassNameForBinding(binding);
    List<TypeVariableName> typeParameters = bindingTypeElementTypeVariableNames(binding);
    return typeParameters.isEmpty()
        ? className
        : ParameterizedTypeName.get(className, typeParameters.toArray(new TypeName[0]));
  }

  public static ClassName membersInjectorNameForType(TypeElement typeElement) {
    return siblingClassName(typeElement, "_MembersInjector");
  }

  public static String memberInjectedFieldSignatureForVariable(VariableElement variableElement) {
    return MoreElements.asType(variableElement.getEnclosingElement()).getQualifiedName()
        + "."
        + variableElement.getSimpleName();
  }

  public static String classFileName(ClassName className) {
    return String.join("_", className.simpleNames());
  }

  // TODO(ronshapiro): when JavaPoet migration is complete, replace the duplicated code
  // which could use this.
  private static ClassName siblingClassName(TypeElement typeElement, String suffix) {
    ClassName className = ClassName.get(typeElement);
    return className.topLevelClassName().peerClass(classFileName(className) + suffix);
  }

  /**
   * The {@link java.util.Set} factory class name appropriate for set bindings.
   *
   * <ul>
   *   <li>{@link SetFactory} for provision bindings.
   * </ul>
   */
  public static ClassName setFactoryClassName(ContributionBinding binding) {
    Preconditions.checkArgument(binding.kind().equals(MULTIBOUND_SET));
    return SET_FACTORY;
  }

  /** The {@link java.util.Map} factory class name appropriate for map bindings. */
  public static ClassName mapFactoryClassName(ContributionBinding binding) {
    Preconditions.checkState(binding.kind().equals(MULTIBOUND_MAP), binding.kind());
    MapType mapType = MapType.from(binding.key());
    if (binding.bindingType() != BindingType.PROVISION) {
      throw new IllegalArgumentException(binding.bindingType().toString());
    }
    return mapType.valuesAreTypeOf(Provider.class) ? MAP_PROVIDER_FACTORY : MAP_FACTORY;
  }

  public static List<TypeVariableName> bindingTypeElementTypeVariableNames(
      Binding binding) {
    if (binding instanceof ContributionBinding) {
      ContributionBinding contributionBinding = (ContributionBinding) binding;
      if (!(contributionBinding.kind() == INJECTION
          || contributionBinding.kind() == ASSISTED_INJECTION)
          && !contributionBinding.requiresModuleInstance()) {
        return List.of();
      }
    }
    List<? extends TypeParameterElement> typeParameters =
        binding.bindingTypeElement().get().getTypeParameters();
    return typeParameters.stream().map(TypeVariableName::get).collect(toImmutableList());
  }

  /**
   * Returns a name to be used for variables of the given {@linkplain TypeElement type}. Prefer
   * semantically meaningful variable names, but if none can be derived, this will produce something
   * readable.
   */
  // TODO(gak): maybe this should be a function of TypeMirrors instead of Elements?
  public static String simpleVariableName(TypeElement typeElement) {
    return simpleVariableName(ClassName.get(typeElement));
  }

  /**
   * Returns a name to be used for variables of the given {@linkplain ClassName}. Prefer
   * semantically meaningful variable names, but if none can be derived, this will produce something
   * readable.
   */
  public static String simpleVariableName(ClassName className) {
    String simpleName = className.simpleName();
    String candidateName = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    String variableName = protectAgainstKeywords(candidateName);
    Preconditions.checkState(isName(variableName), "'%s' was expected to be a valid variable name");
    return variableName;
  }

  public static String protectAgainstKeywords(String candidateName) {
    switch (candidateName) {
      case "package":
        return "pkg";
      case "boolean":
      case "byte":
        return "b";
      case "double":
        return "d";
      case "int":
        return "i";
      case "short":
        return "s";
      case "char":
        return "c";
      case "void":
        return "v";
      case "class":
        return "clazz";
      case "float":
        return "f";
      case "long":
        return "l";
      default:
        return SourceVersion.isKeyword(candidateName) ? candidateName + '_' : candidateName;
    }
  }

  private SourceFiles() {
  }
}
