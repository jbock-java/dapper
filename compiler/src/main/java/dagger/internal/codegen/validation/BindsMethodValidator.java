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

import static dagger.internal.codegen.validation.BindingElementValidator.AllowsScoping.ALLOWS_SCOPING;
import static dagger.internal.codegen.validation.BindingMethodValidator.Abstractness.MUST_BE_ABSTRACT;
import static dagger.internal.codegen.validation.BindingMethodValidator.ExceptionSuperclass.NO_EXCEPTIONS;
import static dagger.internal.codegen.validation.TypeHierarchyValidator.validateTypeHierarchy;
import static dagger.internal.codegen.xprocessing.XTypes.isPrimitive;

import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XVariableElement;
import jakarta.inject.Inject;
import java.util.Set;

/** A validator for {@link dagger.Binds} methods. */
final class BindsMethodValidator extends BindingMethodValidator {
  private final DaggerTypes types;

  @Inject
  BindsMethodValidator(
      XProcessingEnv processingEnv,
      DaggerElements elements,
      DaggerTypes types,
      DependencyRequestValidator dependencyRequestValidator,
      InjectionAnnotations injectionAnnotations) {
    super(
        processingEnv,
        elements,
        types,
        TypeNames.BINDS,
        Set.of(TypeNames.MODULE),
        dependencyRequestValidator,
        MUST_BE_ABSTRACT,
        NO_EXCEPTIONS,
        ALLOWS_SCOPING,
        injectionAnnotations);
    this.types = types;
  }

  @Override
  protected ElementValidator elementValidator(XMethodElement method) {
    return new Validator(method);
  }

  private class Validator extends MethodValidator {
    private final XMethodElement method;

    Validator(XMethodElement method) {
      super(method);
      this.method = method;
    }

    @Override
    protected void checkParameters() {
      if (method.getParameters().size() != 1) {
        report.addError(
            bindingMethods(
                "must have exactly one parameter, whose type is assignable to the return type"));
      } else {
        super.checkParameters();
      }
    }

    @Override
    protected void checkParameter(XVariableElement parameter) {
      super.checkParameter(parameter);
      XType leftHandSide = boxIfNecessary(method.getReturnType());
      XType rightHandSide = parameter.getType();

      if (!types.isAssignable(rightHandSide.toJavac(), leftHandSide.toJavac())) {
        // Validate the type hierarchy of both sides to make sure they're both valid.
        // If one of the types isn't valid it means we need to delay validation to the next round.
        // Note: BasicAnnotationProcessor only performs superficial validation on the referenced
        // types within the module. Thus, we're guaranteed that the types in the @Binds method are
        // valid, but it says nothing about their supertypes, which are needed for isAssignable.
        validateTypeHierarchy(leftHandSide, types);
        validateTypeHierarchy(rightHandSide, types);
        // TODO(ronshapiro): clarify this error message for @ElementsIntoSet cases, where the
        // right-hand-side might not be assignable to the left-hand-side, but still compatible with
        // Set.addAll(Collection<? extends E>)
        report.addError("@Binds methods' parameter type must be assignable to the return type");
      }
    }

    private XType boxIfNecessary(XType maybePrimitive) {
      return isPrimitive(maybePrimitive) ? maybePrimitive.boxed() : maybePrimitive;
    }
  }
}
