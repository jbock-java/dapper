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
import static dagger.internal.codegen.javapoet.TypeNames.PROVIDER_OF_LAZY;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.spi.model.BindingKind.ASSISTED_INJECTION;
import static dagger.spi.model.BindingKind.INJECTION;
import static io.jbock.auto.common.MoreElements.asExecutable;
import static io.jbock.auto.common.MoreElements.asType;
import static javax.lang.model.SourceVersion.isName;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Util;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.RequestKind;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.ParameterizedTypeName;
import io.jbock.javapoet.TypeName;
import io.jbock.javapoet.TypeVariableName;
import java.util.List;
import java.util.Map;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;

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
        FrameworkTypeMapper.forBindingType();

    return Util.toMap(
        binding.dependencies(),
        dependency ->
            FrameworkField.create(
                frameworkTypeMapper.getFrameworkType().frameworkClassName(),
                TypeName.get(dependency.key().type().java()),
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

  /** Returns the generated factory or members injector name for a binding. */
  public static ClassName generatedClassNameForBinding(Binding binding) {
    ContributionBinding contribution = (ContributionBinding) binding;
    switch (contribution.kind()) {
      case ASSISTED_INJECTION:
      case INJECTION:
      case PROVISION:
        return elementBasedClassName(
            asExecutable(toJavac(binding.bindingElement().get())), "Factory");

      case ASSISTED_FACTORY:
        return siblingClassName(asType(toJavac(binding.bindingElement().get())), "_Impl");

      default:
        throw new AssertionError();
    }
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
        ClassName.get(asType(element.getEnclosingElement()));
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

  public static String classFileName(ClassName className) {
    return String.join("_", className.simpleNames());
  }

  // TODO(ronshapiro): when JavaPoet migration is complete, replace the duplicated code
  // which could use this.
  private static ClassName siblingClassName(TypeElement typeElement, String suffix) {
    ClassName className = ClassName.get(typeElement);
    return className.topLevelClassName().peerClass(classFileName(className) + suffix);
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
        toJavac(binding.bindingTypeElement().get()).getTypeParameters();
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
