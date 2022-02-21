/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static dagger.internal.codegen.base.RequestKinds.extractKeyType;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedFactoryType;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedInjectionType;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isWildcard;

import dagger.internal.codegen.base.RequestKinds;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XAnnotation;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.spi.model.RequestKind;
import jakarta.inject.Inject;
import java.util.Set;

/** Validation for dependency requests. */
final class DependencyRequestValidator {
  private final XProcessingEnv processingEnv;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  DependencyRequestValidator(
      XProcessingEnv processingEnv,
      InjectionAnnotations injectionAnnotations) {
    this.processingEnv = processingEnv;
    this.injectionAnnotations = injectionAnnotations;
  }

  /**
   * Adds an error if the given dependency request has more than one qualifier annotation or is a
   * non-instance request with a wildcard type.
   */
  void validateDependencyRequest(
      ValidationReport.Builder report, XElement requestElement, XType requestType) {
    if (requestElement.hasAnnotation(TypeNames.ASSISTED)) {
      // Don't validate assisted parameters. These are not dependency requests.
      return;
    }

    new Validator(report, requestElement, requestType).validate();
  }

  private final class Validator {
    private final ValidationReport.Builder report;
    private final XElement requestElement;
    private final XType requestType;
    private final XType keyType;
    private final Set<XAnnotation> qualifiers;


    Validator(ValidationReport.Builder report, XElement requestElement, XType requestType) {
      this.report = report;
      this.requestElement = requestElement;
      this.requestType = requestType;
      this.keyType = extractKeyType(requestType);
      this.qualifiers = injectionAnnotations.getQualifiers(requestElement);
    }

    void validate() {
      checkQualifiers();
      checkType();
    }

    private void checkQualifiers() {
      if (qualifiers.size() > 1) {
        for (XAnnotation qualifier : qualifiers) {
          report.addError(
              "A single dependency request may not use more than one @Qualifier",
              requestElement,
              qualifier);
        }
      }
    }

    private void checkType() {
      if (qualifiers.isEmpty() && isDeclared(keyType)) {
        XTypeElement typeElement = keyType.getTypeElement();
        if (isAssistedInjectionType(typeElement)) {
          report.addError(
              "Dagger does not support injecting @AssistedInject type, "
                  + requestType
                  + ". Did you mean to inject its assisted factory type instead?",
              requestElement);
        }
        RequestKind requestKind = RequestKinds.getRequestKind(requestType);
        if (!(requestKind == RequestKind.INSTANCE || requestKind == RequestKind.PROVIDER)
            && isAssistedFactoryType(typeElement)) {
          report.addError(
              "Dagger does not support injecting Lazy<T>, Producer<T>, "
                  + "or Produced<T> when T is an @AssistedFactory-annotated type such as "
                  + keyType,
              requestElement);
        }
      }
      if (isWildcard(keyType)) {
        // TODO(ronshapiro): Explore creating this message using RequestKinds.
        report.addError(
            "Dagger does not support injecting Provider<T>, Lazy<T>, Producer<T>, "
                + "or Produced<T> when T is a wildcard type such as "
                + keyType,
            requestElement);
      }
    }
  }
}
