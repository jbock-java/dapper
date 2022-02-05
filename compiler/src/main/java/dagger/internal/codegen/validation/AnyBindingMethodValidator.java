/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static dagger.internal.codegen.base.Util.getOnlyElement;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static java.util.stream.Collectors.joining;

import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.xprocessing.XExecutableElement;
import dagger.internal.codegen.xprocessing.XMethodElement;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Validates any binding method. */
@Singleton
public final class AnyBindingMethodValidator implements ClearableCache {
  private final Map<ClassName, BindingMethodValidator> validators;
  private final Map<XExecutableElement, ValidationReport> reports = new HashMap<>();

  @Inject
  AnyBindingMethodValidator(
      Map<ClassName, BindingMethodValidator> validators) {
    this.validators = validators;
  }

  @Override
  public void clearCache() {
    reports.clear();
  }

  /** Returns the binding method annotations considered by this validator. */
  Set<ClassName> methodAnnotations() {
    return validators.keySet();
  }

  /**
   * Returns {@code true} if {@code method} is annotated with at least one of {@link
   * #methodAnnotations()}.
   */
  boolean isBindingMethod(XExecutableElement method) {
    return method.hasAnyOf(methodAnnotations());
  }

  /**
   * Returns a validation report for a method.
   *
   * <ul>
   *   <li>Reports an error if {@code method} is annotated with more than one {@linkplain
   *       #methodAnnotations() binding method annotation}.
   *   <li>Validates {@code method} with the {@link BindingMethodValidator} for the single
   *       {@linkplain #methodAnnotations() binding method annotation}.
   * </ul>
   *
   * @throws IllegalArgumentException if {@code method} is not annotated by any {@linkplain
   *     #methodAnnotations() binding method annotation}
   */
  ValidationReport validate(XExecutableElement method) {
    return reentrantComputeIfAbsent(reports, method, this::validateUncached);
  }

  /**
   * Returns {@code true} if {@code method} was already {@linkplain #validate(XExecutableElement)
   * validated}.
   */
  boolean wasAlreadyValidated(XExecutableElement method) {
    return reports.containsKey(method);
  }

  private ValidationReport validateUncached(XExecutableElement method) {
    ValidationReport.Builder report = ValidationReport.about(method);
    Set<ClassName> bindingMethodAnnotations =
        methodAnnotations().stream().filter(method::hasAnnotation).collect(toImmutableSet());
    switch (bindingMethodAnnotations.size()) {
      case 0:
        throw new IllegalArgumentException(
            String.format("%s has no binding method annotation", method));

      case 1:
        report.addSubreport(
            validators.get(getOnlyElement(bindingMethodAnnotations)).validate(method));
        break;

      default:
        // This is a validator for binding methods, so the passed in element must be a method
        // element.
        Preconditions.checkArgument(
            method instanceof XMethodElement, "%s must be instanceof XMethodElement.", method);
        report.addError(
            String.format(
                "%s is annotated with more than one of (%s)",
                ((XMethodElement) method).getName(),
                methodAnnotations().stream().map(ClassName::canonicalName).collect(joining(", "))),
            method);
        break;
    }
    return report.build();
  }
}
