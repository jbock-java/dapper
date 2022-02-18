/*
 * Copyright (C) 2021 The Dagger Authors.
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

import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedInjectAssistedParameters;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations.AssistedParameter;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.validation.SuperficialValidator;
import dagger.internal.codegen.validation.TypeCheckingProcessingStep;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.xprocessing.XConstructorElement;
import dagger.internal.codegen.xprocessing.XConverters;
import dagger.internal.codegen.xprocessing.XMessager;
import dagger.internal.codegen.xprocessing.XType;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;

/** An annotation processor for {@link dagger.assisted.AssistedInject}-annotated elements. */
final class AssistedInjectProcessingStep extends TypeCheckingProcessingStep<XConstructorElement> {
  private final XMessager messager;

  @Inject
  AssistedInjectProcessingStep(
      SuperficialValidator elementValidator,
      XMessager messager) {
    super(elementValidator);
    this.messager = messager;
  }

  @Override
  public Set<ClassName> annotationClassNames() {
    return Set.of(TypeNames.ASSISTED_INJECT);
  }

  @Override
  protected void process(
      XConstructorElement assistedInjectElement, Set<ClassName> annotations) {
    new AssistedInjectValidator().validate(assistedInjectElement).printMessagesTo(messager);
  }

  private static final class AssistedInjectValidator {
    ValidationReport validate(XConstructorElement constructor) {
      ExecutableElement javaConstructor = XConverters.toJavac(constructor);
      Preconditions.checkState(javaConstructor.getKind() == ElementKind.CONSTRUCTOR);
      ValidationReport.Builder report = ValidationReport.about(constructor);

      XType assistedInjectType = constructor.getEnclosingElement().getType();
      List<AssistedParameter> assistedParameters =
          assistedInjectAssistedParameters(assistedInjectType);

      Set<AssistedParameter> uniqueAssistedParameters = new HashSet<>();
      for (AssistedParameter assistedParameter : assistedParameters) {
        if (!uniqueAssistedParameters.add(assistedParameter)) {
          report.addError(
              String.format(
                  "@AssistedInject constructor has duplicate @Assisted type: %s. Consider setting"
                      + " an identifier on the parameter by using @Assisted(\"identifier\") in both"
                      + " the factory and @AssistedInject constructor",
                  assistedParameter),
              assistedParameter.element());
        }
      }

      return report.build();
    }
  }
}
