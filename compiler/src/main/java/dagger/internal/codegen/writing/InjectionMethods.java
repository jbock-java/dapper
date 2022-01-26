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

import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.auto.common.MoreElements.asType;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.base.RequestKinds.requestTypeName;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.binding.SourceFiles.protectAgainstKeywords;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.rawTypeName;
import static dagger.internal.codegen.langmodel.Accessibility.isElementAccessibleFrom;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypePubliclyAccessible;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.VOID;

import com.google.auto.common.MoreElements;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.model.DependencyRequest;
import dagger.model.RequestKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** Convenience methods for creating and invoking {@code InjectionMethod}s. */
final class InjectionMethods {

  /**
   * A method that returns an object from a {@code @Provides} method or an {@code @Inject}ed
   * constructor. Its parameters match the dependency requests for constructor and members
   * injection.
   *
   * <p>For {@code @Provides} methods named "foo", the method name is "proxyFoo". For example:
   *
   * <pre><code>
   * abstract class FooModule {
   *   {@literal @Provides} static Foo provideFoo(Bar bar, Baz baz) { … }
   * }
   *
   * public static proxyProvideFoo(Bar bar, Baz baz) { … }
   * </code></pre>
   *
   * <p>For {@code @Inject}ed constructors, the method name is "newFoo". For example:
   *
   * <pre><code>
   * class Foo {
   *   {@literal @Inject} Foo(Bar bar) {}
   * }
   *
   * public static Foo newFoo(Bar bar) { … }
   * </code></pre>
   */
  static final class ProvisionMethod {
    // These names are already defined in factories and shouldn't be used for the proxy method name.
    private static final Set<String> BANNED_PROXY_NAMES = Set.of("get", "create");

    /**
     * Returns a method that invokes the binding's {@linkplain ProvisionBinding#bindingElement()
     * constructor} and injects the instance's members.
     */
    static MethodSpec create(
        ProvisionBinding binding,
        CompilerOptions compilerOptions) {
      ExecutableElement element = asExecutable(binding.bindingElement().orElseThrow());
      switch (element.getKind()) {
        case CONSTRUCTOR:
          return constructorProxy(element);
        case METHOD:
          return methodProxy(
              element,
              methodName(element),
              InstanceCastPolicy.IGNORE,
              CheckNotNullPolicy.get(binding, compilerOptions)
          );
        default:
          throw new AssertionError(element);
      }
    }

    /**
     * Invokes the injection method for {@code binding}, with the dependencies transformed with the
     * {@code dependencyUsage} function.
     */
    static CodeBlock invoke(
        ProvisionBinding binding,
        Function<DependencyRequest, CodeBlock> dependencyUsage,
        Function<VariableElement, String> uniqueAssistedParameterName,
        ClassName requestingClass,
        Optional<CodeBlock> moduleReference,
        CompilerOptions compilerOptions) {
      List<CodeBlock> arguments = new ArrayList<>();
      moduleReference.ifPresent(arguments::add);
      arguments.addAll(invokeArguments(binding, dependencyUsage, uniqueAssistedParameterName, requestingClass));

      ClassName enclosingClass = generatedClassNameForBinding(binding);
      MethodSpec methodSpec = create(binding, compilerOptions);
      return invokeMethod(methodSpec, arguments, enclosingClass, requestingClass);
    }

    static List<CodeBlock> invokeArguments(
        ProvisionBinding binding,
        Function<DependencyRequest, CodeBlock> dependencyUsage,
        Function<VariableElement, String> uniqueAssistedParameterName,
        ClassName requestingClass) {
      Map<VariableElement, DependencyRequest> dependencyRequestMap =
          binding.provisionDependencies().stream()
              .collect(
                  toImmutableMap(
                      request -> MoreElements.asVariable(request.requestElement().orElseThrow()),
                      request -> request));

      List<CodeBlock> arguments = new ArrayList<>();
      ExecutableElement method = asExecutable(binding.bindingElement().orElseThrow());
      for (VariableElement parameter : method.getParameters()) {
        if (AssistedInjectionAnnotations.isAssistedParameter(parameter)) {
          arguments.add(CodeBlock.of("$L", uniqueAssistedParameterName.apply(parameter)));
        } else if (dependencyRequestMap.containsKey(parameter)) {
          DependencyRequest request = dependencyRequestMap.get(parameter);
          arguments.add(
              injectionMethodArgument(request, dependencyUsage.apply(request), requestingClass));
        } else {
          throw new AssertionError("Unexpected parameter: " + parameter);
        }
      }

      return arguments;
    }

    private static MethodSpec constructorProxy(ExecutableElement constructor) {
      TypeElement enclosingType = MoreElements.asType(constructor.getEnclosingElement());
      MethodSpec.Builder builder =
          methodBuilder(methodName(constructor))
              .addModifiers(PUBLIC, STATIC)
              .varargs(constructor.isVarArgs())
              .returns(TypeName.get(enclosingType.asType()));

      copyTypeParameters(builder, enclosingType);
      copyThrows(builder, constructor);

      CodeBlock arguments =
          copyParameters(builder, new UniqueNameSet(), constructor.getParameters());
      return builder.addStatement("return new $T($L)", enclosingType, arguments).build();
    }

    /**
     * Returns {@code true} if injecting an instance of {@code binding} from {@code callingPackage}
     * requires the use of an injection method.
     */
    static boolean requiresInjectionMethod(
        ProvisionBinding binding, CompilerOptions compilerOptions, ClassName requestingClass) {
      ExecutableElement method = MoreElements.asExecutable(binding.bindingElement().orElseThrow());
      return binding.shouldCheckForNull(compilerOptions)
          || !isElementAccessibleFrom(method, requestingClass.packageName())
          // This check should be removable once we drop support for -source 7
          || method.getParameters().stream()
          .map(VariableElement::asType)
          .anyMatch(type -> !isRawTypeAccessible(type, requestingClass.packageName()));
    }

    /**
     * Returns the name of the {@code static} method that wraps {@code method}.
     */
    private static String methodName(ExecutableElement method) {
      switch (method.getKind()) {
        case CONSTRUCTOR:
          return "newInstance";
        case METHOD:
          String methodName = method.getSimpleName().toString();
          return BANNED_PROXY_NAMES.contains(methodName)
              ? "proxy" + Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1)
              : methodName;
        default:
          throw new AssertionError(method);
      }
    }
  }

  private static CodeBlock injectionMethodArgument(
      DependencyRequest dependency, CodeBlock argument, ClassName generatedTypeName) {
    TypeMirror keyType = dependency.key().type().java();
    CodeBlock.Builder codeBlock = CodeBlock.builder();
    if (!isRawTypeAccessible(keyType, generatedTypeName.packageName())
        && isTypeAccessibleFrom(keyType, generatedTypeName.packageName())) {
      if (!dependency.kind().equals(RequestKind.INSTANCE)) {
        TypeName usageTypeName = accessibleType(dependency);
        codeBlock.add("($T) ($T)", usageTypeName, rawTypeName(usageTypeName));
      } else if (dependency.requestElement().orElseThrow().asType().getKind().equals(TypeKind.TYPEVAR)) {
        codeBlock.add("($T)", keyType);
      }
    }
    return codeBlock.add(argument).build();
  }

  /**
   * Returns the parameter type for {@code dependency}. If the raw type is not accessible, returns
   * {@link Object}.
   */
  private static TypeName accessibleType(DependencyRequest dependency) {
    TypeName typeName =
        requestTypeName(dependency.kind(), accessibleType(dependency.key().type().java()));
    return dependency
        .requestElement()
        .map(element -> element.asType().getKind().isPrimitive())
        .orElse(false)
        ? typeName.unbox()
        : typeName;
  }

  /**
   * Returns the accessible type for {@code type}. If the raw type is not accessible, returns {@link
   * Object}.
   */
  private static TypeName accessibleType(TypeMirror type) {
    return isRawTypePubliclyAccessible(type) ? TypeName.get(type) : TypeName.OBJECT;
  }

  private enum InstanceCastPolicy {
    CAST_IF_NOT_PUBLIC, IGNORE;

    boolean useObjectType(TypeMirror instanceType) {
      return this == CAST_IF_NOT_PUBLIC && !isRawTypePubliclyAccessible(instanceType);
    }
  }

  private enum CheckNotNullPolicy {
    IGNORE, CHECK_FOR_NULL;

    CodeBlock checkForNull(CodeBlock maybeNull) {
      return this.equals(IGNORE)
          ? maybeNull
          : CodeBlock.of("$T.checkNotNullFromProvides($L)", dagger.internal.Preconditions.class, maybeNull);
    }

    static CheckNotNullPolicy get(ProvisionBinding binding, CompilerOptions compilerOptions) {
      return binding.shouldCheckForNull(compilerOptions) ? CHECK_FOR_NULL : IGNORE;
    }
  }

  private static MethodSpec methodProxy(
      ExecutableElement method,
      String methodName,
      InstanceCastPolicy instanceCastPolicy,
      CheckNotNullPolicy checkNotNullPolicy) {
    MethodSpec.Builder builder =
        methodBuilder(methodName).addModifiers(PUBLIC, STATIC).varargs(method.isVarArgs());

    TypeElement enclosingType = asType(method.getEnclosingElement());
    UniqueNameSet parameterNameSet = new UniqueNameSet();
    CodeBlock instance;
    if (method.getModifiers().contains(STATIC)) {
      instance = CodeBlock.of("$T", rawTypeName(TypeName.get(enclosingType.asType())));
    } else {
      copyTypeParameters(builder, enclosingType);
      boolean useObject = instanceCastPolicy.useObjectType(enclosingType.asType());
      instance = copyInstance(builder, parameterNameSet, enclosingType.asType(), useObject);
    }
    CodeBlock arguments = copyParameters(builder, parameterNameSet, method.getParameters());
    CodeBlock invocation =
        checkNotNullPolicy.checkForNull(
            CodeBlock.of("$L.$L($L)", instance, method.getSimpleName(), arguments));

    copyTypeParameters(builder, method);
    copyThrows(builder, method);

    if (method.getReturnType().getKind().equals(VOID)) {
      return builder.addStatement("$L", invocation).build();
    } else {
      return builder
          .returns(TypeName.get(method.getReturnType()))
          .addStatement("return $L", invocation).build();
    }
  }

  private static CodeBlock invokeMethod(
      MethodSpec methodSpec,
      List<CodeBlock> parameters,
      ClassName enclosingClass,
      ClassName requestingClass) {
    Preconditions.checkArgument(methodSpec.parameters.size() == parameters.size());
    CodeBlock parameterBlock = makeParametersCodeBlock(parameters);
    return enclosingClass.equals(requestingClass)
        ? CodeBlock.of("$L($L)", methodSpec.name, parameterBlock)
        : CodeBlock.of("$T.$L($L)", enclosingClass, methodSpec.name, parameterBlock);
  }

  private static void copyTypeParameters(
      MethodSpec.Builder methodBuilder, Parameterizable element) {
    element.getTypeParameters().stream()
        .map(TypeVariableName::get)
        .forEach(methodBuilder::addTypeVariable);
  }

  private static void copyThrows(MethodSpec.Builder methodBuilder, ExecutableElement method) {
    method.getThrownTypes().stream().map(TypeName::get).forEach(methodBuilder::addException);
  }

  private static CodeBlock copyParameters(
      MethodSpec.Builder methodBuilder,
      UniqueNameSet parameterNameSet,
      List<? extends VariableElement> parameters) {
    return parameters.stream()
        .map(
            parameter -> {
              String name =
                  parameterNameSet.getUniqueName(validJavaName(parameter.getSimpleName()));
              TypeMirror type = parameter.asType();
              boolean useObject = !isRawTypePubliclyAccessible(type);
              return copyParameter(methodBuilder, type, name, useObject);
            })
        .collect(toParametersCodeBlock());
  }

  private static CodeBlock copyParameter(
      MethodSpec.Builder methodBuilder, TypeMirror type, String name, boolean useObject) {
    TypeName typeName = useObject ? TypeName.OBJECT : TypeName.get(type);
    methodBuilder.addParameter(ParameterSpec.builder(typeName, name).build());
    return useObject ? CodeBlock.of("($T) $L", type, name) : CodeBlock.of("$L", name);
  }

  private static CodeBlock copyInstance(
      MethodSpec.Builder methodBuilder,
      UniqueNameSet parameterNameSet,
      TypeMirror type,
      boolean useObject) {
    CodeBlock instance =
        copyParameter(methodBuilder, type, parameterNameSet.getUniqueName("instance"), useObject);
    // If we had to cast the instance add an extra parenthesis incase we're calling a method on it.
    return useObject ? CodeBlock.of("($L)", instance) : instance;
  }

  private static String validJavaName(CharSequence name) {
    if (SourceVersion.isIdentifier(name)) {
      return protectAgainstKeywords(name.toString());
    }

    StringBuilder newName = new StringBuilder(name.length());
    char firstChar = name.charAt(0);
    if (!Character.isJavaIdentifierStart(firstChar)) {
      newName.append('_');
    }

    name.chars().forEach(c -> newName.append(Character.isJavaIdentifierPart(c) ? c : '_'));
    return newName.toString();
  }
}
