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

import static dagger.internal.codegen.validation.BindingElementValidator.AllowsScoping.ALLOWS_SCOPING;
import static dagger.internal.codegen.validation.BindingMethodValidator.Abstractness.MUST_BE_CONCRETE;
import static dagger.internal.codegen.validation.BindingMethodValidator.ExceptionSuperclass.RUNTIME_EXCEPTION;

import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.xprocessing.XExecutableElement;
import jakarta.inject.Inject;
import java.util.Set;

/** A validator for {@link dagger.Provides} methods. */
final class ProvidesMethodValidator extends BindingMethodValidator {

  @Inject
  ProvidesMethodValidator(
      DaggerElements elements,
      DaggerTypes types,
      DependencyRequestValidator dependencyRequestValidator,
      InjectionAnnotations injectionAnnotations) {
    super(
        elements,
        types,
        TypeNames.PROVIDES,
        Set.of(TypeNames.MODULE),
        dependencyRequestValidator,
        MUST_BE_CONCRETE,
        RUNTIME_EXCEPTION,
        ALLOWS_SCOPING,
        injectionAnnotations);
  }

  @Override
  protected ElementValidator elementValidator(XExecutableElement xElement) {
    return new Validator(xElement);
  }

  private class Validator extends MethodValidator {
    Validator(XExecutableElement xElement) {
      super(xElement);
    }

    @Override
    protected void checkAdditionalMethodProperties() {
    }
  }
}
