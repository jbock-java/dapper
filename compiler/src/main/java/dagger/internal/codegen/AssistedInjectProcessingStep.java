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

import dagger.internal.codegen.binding.AssistedInjectionAnnotations.AssistedParameter;
import dagger.internal.codegen.collect.ImmutableList;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.validation.TypeCheckingProcessingStep;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.xprocessing.XConstructorElement;
import dagger.internal.codegen.xprocessing.XMessager;
import dagger.internal.codegen.xprocessing.XType;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.Set;

/** An annotation processor for {@code dagger.assisted.AssistedInject}-annotated elements. */
final class AssistedInjectProcessingStep extends TypeCheckingProcessingStep<XConstructorElement> {
  private final XMessager messager;

  @Inject
  AssistedInjectProcessingStep(XMessager messager) {
    this.messager = messager;
  }

  @Override
  public ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(TypeNames.ASSISTED_INJECT);
  }

  @Override
  protected void process(
      XConstructorElement assistedInjectElement, ImmutableSet<ClassName> annotations) {
    new AssistedInjectValidator().validate(assistedInjectElement).printMessagesTo(messager);
  }

  private final class AssistedInjectValidator {
    ValidationReport validate(XConstructorElement constructor) {
      ValidationReport.Builder report = ValidationReport.about(constructor);

      XType assistedInjectType = constructor.getEnclosingElement().getType();
      ImmutableList<AssistedParameter> assistedParameters =
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
