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

package dagger.internal.codegen.validation;

import static dagger.internal.codegen.base.Scopes.scopesOf;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedInjectedConstructors;
import static dagger.internal.codegen.binding.InjectionAnnotations.injectedConstructors;
import static dagger.internal.codegen.xprocessing.XConverters.toXProcessing;
import static io.jbock.auto.common.MoreElements.asType;
import static io.jbock.auto.common.MoreElements.isAnnotationPresent;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.DECLARED;

import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.Accessibility;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.model.Scope;
import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;

/**
 * A {@linkplain ValidationReport validator} for {@code Inject}-annotated elements and the types
 * that contain them.
 */
@Singleton
public final class InjectValidator implements ClearableCache {
  private final XProcessingEnv processingEnv;
  private final DaggerTypes types;
  private final DaggerElements elements;
  private final CompilerOptions compilerOptions;
  private final DependencyRequestValidator dependencyRequestValidator;
  private final Optional<Diagnostic.Kind> privateAndStaticInjectionDiagnosticKind;
  private final InjectionAnnotations injectionAnnotations;
  private final Map<ExecutableElement, ValidationReport> reports = new HashMap<>();

  @Inject
  InjectValidator(
      XProcessingEnv processingEnv,
      DaggerTypes types,
      DaggerElements elements,
      DependencyRequestValidator dependencyRequestValidator,
      CompilerOptions compilerOptions,
      InjectionAnnotations injectionAnnotations) {
    this(
        processingEnv,
        types,
        elements,
        compilerOptions,
        dependencyRequestValidator,
        Optional.empty(),
        injectionAnnotations);
  }

  private InjectValidator(
      XProcessingEnv processingEnv,
      DaggerTypes types,
      DaggerElements elements,
      CompilerOptions compilerOptions,
      DependencyRequestValidator dependencyRequestValidator,
      Optional<Kind> privateAndStaticInjectionDiagnosticKind,
      InjectionAnnotations injectionAnnotations) {
    this.processingEnv = processingEnv;
    this.types = types;
    this.elements = elements;
    this.compilerOptions = compilerOptions;
    this.dependencyRequestValidator = dependencyRequestValidator;
    this.privateAndStaticInjectionDiagnosticKind = privateAndStaticInjectionDiagnosticKind;
    this.injectionAnnotations = injectionAnnotations;
  }

  @Override
  public void clearCache() {
    reports.clear();
  }

  /**
   * Returns a new validator that performs the same validation as this one, but is strict about
   * rejecting optionally-specified JSR 330 behavior that Dagger doesn't support (unless {@code
   * -Adagger.ignorePrivateAndStaticInjectionForComponent=enabled} was set in the javac options).
   */
  public InjectValidator whenGeneratingCode() {
    return compilerOptions.ignorePrivateAndStaticInjectionForComponent()
        ? this
        : new InjectValidator(
        processingEnv,
        types,
        elements,
        compilerOptions,
        dependencyRequestValidator,
        Optional.of(Diagnostic.Kind.ERROR),
        injectionAnnotations);
  }

  public ValidationReport validateConstructor(ExecutableElement constructorElement) {
    return reentrantComputeIfAbsent(reports, constructorElement, this::validateConstructorUncached);
  }

  private ValidationReport validateConstructorUncached(
      ExecutableElement constructorElement) {
    ValidationReport.Builder builder =
        ValidationReport.about(asType(constructorElement.getEnclosingElement()));

    if (isAnnotationPresent(constructorElement, Inject.class)
        && isAnnotationPresent(constructorElement, AssistedInject.class)) {
      builder.addError("Constructors cannot be annotated with both @Inject and @AssistedInject");
    }

    Class<?> injectAnnotation =
        isAnnotationPresent(constructorElement, Inject.class) ? Inject.class : AssistedInject.class;

    if (constructorElement.getModifiers().contains(PRIVATE)) {
      builder.addError(
          "Dagger does not support injection into private constructors", constructorElement);
    }

    for (AnnotationMirror qualifier : injectionAnnotations.getQualifiers(constructorElement)) {
      builder.addError(
          String.format(
              "@Qualifier annotations are not allowed on @%s constructors",
              injectAnnotation.getSimpleName()),
          constructorElement,
          qualifier);
    }

    String scopeErrorMsg =
        String.format(
            "@Scope annotations are not allowed on @%s constructors",
            injectAnnotation.getSimpleName());

    if (injectAnnotation == Inject.class) {
      scopeErrorMsg += "; annotate the class instead";
    }

    for (Scope scope : scopesOf(toXProcessing(constructorElement, processingEnv))) {
      builder.addError(scopeErrorMsg, constructorElement, scope.scopeAnnotation().java());
    }

    for (VariableElement parameter : constructorElement.getParameters()) {
      validateDependencyRequest(builder, parameter);
    }

    if (throwsCheckedExceptions(constructorElement)) {
      builder.addItem(
          String.format(
              "Dagger does not support checked exceptions on @%s constructors",
              injectAnnotation.getSimpleName()),
          privateMemberDiagnosticKind(),
          constructorElement);
    }

    checkInjectIntoPrivateClass(constructorElement, builder);

    TypeElement enclosingElement =
        MoreElements.asType(constructorElement.getEnclosingElement());

    Set<Modifier> typeModifiers = enclosingElement.getModifiers();
    if (typeModifiers.contains(ABSTRACT)) {
      builder.addError(
          String.format(
              "@%s is nonsense on the constructor of an abstract class",
              injectAnnotation.getSimpleName()),
          constructorElement);
    }

    if (enclosingElement.getNestingKind().isNested()
        && !typeModifiers.contains(STATIC)) {
      builder.addError(
          String.format(
              "@%s constructors are invalid on inner classes. "
                  + "Did you mean to make the class static?",
              injectAnnotation.getSimpleName()),
          constructorElement);
    }

    // This is computationally expensive, but probably preferable to a giant index
    Set<ExecutableElement> injectConstructors = new LinkedHashSet<>();
    injectConstructors.addAll(injectedConstructors(enclosingElement));
    injectConstructors.addAll(assistedInjectedConstructors(enclosingElement));

    if (injectConstructors.size() > 1) {
      builder.addError("Types may only contain one injected constructor", constructorElement);
    }

    Set<Scope> scopes = scopesOf(toXProcessing(enclosingElement, processingEnv));
    if (injectAnnotation == AssistedInject.class) {
      for (Scope scope : scopes) {
        builder.addError(
            "A type with an @AssistedInject-annotated constructor cannot be scoped",
            enclosingElement,
            scope.scopeAnnotation().java());
      }
    } else if (scopes.size() > 1) {
      for (Scope scope : scopes) {
        builder.addError(
            "A single binding may not declare more than one @Scope",
            enclosingElement,
            scope.scopeAnnotation().java());
      }
    }

    return builder.build();
  }

  private ValidationReport validateField(VariableElement fieldElement) {
    ValidationReport.Builder builder = ValidationReport.about(fieldElement);
    builder.addItem(
        "Field injection has been disabled",
        privateMemberDiagnosticKind(),
        fieldElement);
    return builder.build();
  }

  private ValidationReport validateMethod(ExecutableElement methodElement) {
    ValidationReport.Builder builder = ValidationReport.about(methodElement);
    builder.addItem(
        "Method injection has been disabled",
        privateMemberDiagnosticKind(),
        methodElement);
    return builder.build();
  }

  private void validateDependencyRequest(
      ValidationReport.Builder builder, VariableElement parameter) {
    dependencyRequestValidator.validateDependencyRequest(builder, parameter, parameter.asType());
  }

  public ValidationReport validateMembersInjectionType(TypeElement typeElement) {
    // TODO(beder): This element might not be currently compiled, so this error message could be
    // left in limbo. Find an appropriate way to display the error message in that case.
    ValidationReport.Builder builder = ValidationReport.about(typeElement);
    for (VariableElement element : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(element, Inject.class)) {
        ValidationReport report = validateField(element);
        builder.addSubreport(report);
      }
    }
    for (ExecutableElement element : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(element, Inject.class)) {
        ValidationReport report = validateMethod(element);
        builder.addSubreport(report);
      }
    }
    TypeMirror superclass = typeElement.getSuperclass();
    if (!superclass.getKind().equals(TypeKind.NONE)) {
      ValidationReport report = validateType(MoreTypes.asTypeElement(superclass));
      if (!report.isClean()) {
        builder.addSubreport(report);
      }
    }
    return builder.build();
  }

  public ValidationReport validateType(TypeElement typeElement) {
    ValidationReport.Builder builder = ValidationReport.about(typeElement);
    for (ExecutableElement element :
        ElementFilter.constructorsIn(typeElement.getEnclosedElements())) {
      if (isAnnotationPresent(element, Inject.class)
          || isAnnotationPresent(element, AssistedInject.class)) {
        ValidationReport report = validateConstructor(element);
        if (!report.isClean()) {
          builder.addSubreport(report);
        }
      }
    }
    return builder.build();
  }

  public boolean isValidType(TypeMirror type) {
    if (!type.getKind().equals(DECLARED)) {
      return true;
    }
    return validateType(MoreTypes.asTypeElement(type)).isClean();
  }

  /** Returns true if the given method element declares a checked exception. */
  private boolean throwsCheckedExceptions(ExecutableElement methodElement) {
    TypeMirror runtimeExceptionType = elements.getTypeElement(TypeNames.RUNTIME_EXCEPTION).asType();
    TypeMirror errorType = elements.getTypeElement(TypeNames.ERROR).asType();
    for (TypeMirror thrownType : methodElement.getThrownTypes()) {
      if (!types.isSubtype(thrownType, runtimeExceptionType)
          && !types.isSubtype(thrownType, errorType)) {
        return true;
      }
    }
    return false;
  }

  private void checkInjectIntoPrivateClass(
      Element element, ValidationReport.Builder builder) {
    if (!Accessibility.isElementAccessibleFromOwnPackage(
        DaggerElements.closestEnclosingTypeElement(element))) {
      builder.addItem(
          "Dagger does not support injection into private classes",
          privateMemberDiagnosticKind(),
          element);
    }
  }

  private Diagnostic.Kind privateMemberDiagnosticKind() {
    return privateAndStaticInjectionDiagnosticKind.orElse(
        compilerOptions.privateMemberValidationKind());
  }
}
