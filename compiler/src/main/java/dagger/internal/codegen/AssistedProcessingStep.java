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

import static dagger.internal.codegen.langmodel.DaggerElements.closestEnclosingTypeElement;
import static io.jbock.auto.common.MoreElements.isAnnotationPresent;

import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.validation.XTypeCheckingProcessingStep;
import dagger.internal.codegen.xprocessing.XConverters;
import dagger.internal.codegen.xprocessing.XMessager;
import dagger.internal.codegen.xprocessing.XVariableElement;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * An annotation processor for {@link dagger.assisted.Assisted}-annotated types.
 *
 * <p>This processing step should run after {@link AssistedFactoryProcessingStep}.
 */
final class AssistedProcessingStep extends XTypeCheckingProcessingStep<XVariableElement> {
  private final InjectionAnnotations injectionAnnotations;
  private final DaggerElements elements;
  private final XMessager messager;

  @Inject
  AssistedProcessingStep(
      InjectionAnnotations injectionAnnotations,
      DaggerElements elements,
      XMessager messager) {
    this.injectionAnnotations = injectionAnnotations;
    this.elements = elements;
    this.messager = messager;
  }

  @Override
  public Set<ClassName> annotationClassNames() {
    return Set.of(TypeNames.ASSISTED);
  }

  @Override
  protected void process(XVariableElement assisted, Set<ClassName> annotations) {
    new AssistedValidator().validate(assisted).printMessagesTo(messager);
  }

  private final class AssistedValidator {
    ValidationReport validate(XVariableElement assisted) {
      ValidationReport.Builder report = ValidationReport.about(assisted);

      VariableElement javaAssisted = XConverters.toJavac(assisted);
      Element enclosingElement = javaAssisted.getEnclosingElement();
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

  private boolean isAssistedInjectConstructor(Element element) {
    return element.getKind() == ElementKind.CONSTRUCTOR
        && isAnnotationPresent(element, AssistedInject.class);
  }

  private boolean isAssistedFactoryCreateMethod(Element element) {
    if (element.getKind() == ElementKind.METHOD) {
      TypeElement enclosingElement = closestEnclosingTypeElement(element);
      return AssistedInjectionAnnotations.isAssistedFactoryType(enclosingElement)
          // This assumes we've already validated AssistedFactory and that a valid method exists.
          && AssistedInjectionAnnotations.assistedFactoryMethod(enclosingElement, elements)
          .equals(element);
    }
    return false;
  }
}
