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

package dagger.internal.codegen;

import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedFactoryMethod;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedFactoryType;
import static dagger.internal.codegen.xprocessing.XElement.isConstructor;
import static dagger.internal.codegen.xprocessing.XElement.isMethod;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;

import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.validation.SuperficialValidator;
import dagger.internal.codegen.validation.TypeCheckingProcessingStep;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.xprocessing.XExecutableElement;
import dagger.internal.codegen.xprocessing.XExecutableParameterElement;
import dagger.internal.codegen.xprocessing.XMessager;
import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.Set;

/**
 * An annotation processor for {@link dagger.assisted.Assisted}-annotated types.
 *
 * <p>This processing step should run after {@link AssistedFactoryProcessingStep}.
 */
final class AssistedProcessingStep extends TypeCheckingProcessingStep<XExecutableParameterElement> {
  private final InjectionAnnotations injectionAnnotations;
  private final XMessager messager;

  @Inject
  AssistedProcessingStep(
      InjectionAnnotations injectionAnnotations,
      SuperficialValidator elementValidator,
      XMessager messager) {
    super(elementValidator, messager);
    this.injectionAnnotations = injectionAnnotations;
    this.messager = messager;
  }

  @Override
  public Set<ClassName> annotationClassNames() {
    return Set.of(TypeNames.ASSISTED);
  }

  @Override
  protected void process(
      XExecutableParameterElement assisted, ImmutableSet<ClassName> annotations) {
    new AssistedValidator().validate(assisted).printMessagesTo(messager);
  }

  private final class AssistedValidator {
    ValidationReport validate(XExecutableParameterElement assisted) {
      ValidationReport.Builder report = ValidationReport.about(assisted);

      XExecutableElement enclosingElement = assisted.getEnclosingMethodElement();
      if (!isAssistedInjectConstructor(enclosingElement)
          && !isAssistedFactoryCreateMethod(enclosingElement)) {
        report.addError(
            "@Assisted parameters can only be used within an @AssistedInject-annotated "
                + "constructor.",
            assisted);
      }

      injectionAnnotations
          .getQualifiers(assisted)
          .forEach(
              qualifier ->
                  report.addError(
                      "Qualifiers cannot be used with @Assisted parameters.", assisted, qualifier));

      return report.build();
    }
  }

  private boolean isAssistedInjectConstructor(XExecutableElement executableElement) {
    return isConstructor(executableElement)
        && executableElement.hasAnnotation(TypeNames.ASSISTED_INJECT);
  }

  private boolean isAssistedFactoryCreateMethod(XExecutableElement executableElement) {
    if (isMethod(executableElement)) {
      XTypeElement enclosingElement = closestEnclosingTypeElement(executableElement);
      return isAssistedFactoryType(enclosingElement)
          // This assumes we've already validated AssistedFactory and that a valid method exists.
          && assistedFactoryMethod(enclosingElement).equals(executableElement);
    }
    return false;
  }
}
