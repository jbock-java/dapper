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

import static dagger.internal.codegen.base.ComponentAnnotation.anyComponentAnnotation;
import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.base.Util.getOnlyElement;

import dagger.internal.codegen.base.ModuleAnnotation;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XExecutableElement;
import dagger.internal.codegen.xprocessing.XVariableElement;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

final class BindsInstanceMethodValidator extends BindsInstanceElementValidator<XExecutableElement> {
  @Inject
  BindsInstanceMethodValidator(InjectionAnnotations injectionAnnotations) {
    super(injectionAnnotations);
  }

  @Override
  protected ElementValidator elementValidator(XExecutableElement xElement) {
    return new Validator(xElement);
  }

  private class Validator extends ElementValidator {
    Validator(XExecutableElement xElement) {
      super(xElement);
    }

    @Override
    protected void checkAdditionalProperties() {
      if (!xElement.isAbstract()) {
        report.addError("@BindsInstance methods must be abstract");
      }
      if (xElement.getParameters().size() != 1) {
        report.addError(
            "@BindsInstance methods should have exactly one parameter for the bound type");
      }
      XElement enclosingElement = xElement.getEnclosingElement();
      moduleAnnotation(enclosingElement)
          .ifPresent(moduleAnnotation -> report.addError(didYouMeanBinds(moduleAnnotation)));
      anyComponentAnnotation(enclosingElement)
          .ifPresent(
              componentAnnotation ->
                  report.addError(
                      String.format(
                          "@BindsInstance methods should not be included in @%1$ss. "
                              + "Did you mean to put it in a @%1$s.Builder?",
                          componentAnnotation.simpleName())));
    }

    @Override
    protected Optional<TypeMirror> bindingElementType() {
      List<? extends XVariableElement> parameters =
          xElement.getParameters();
      return parameters.size() == 1
          ? Optional.of(getOnlyElement(parameters).getType().toJavac())
          : Optional.empty();
    }
  }

  private static String didYouMeanBinds(ModuleAnnotation moduleAnnotation) {
    return String.format(
        "@BindsInstance methods should not be included in @%ss. Did you mean @Binds?",
        moduleAnnotation.annotationName());
  }
}
