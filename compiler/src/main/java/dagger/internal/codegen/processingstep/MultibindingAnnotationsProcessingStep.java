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

package dagger.internal.codegen.processingstep;

import static javax.tools.Diagnostic.Kind.ERROR;

import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.validation.AnyBindingMethodValidator;
import dagger.internal.codegen.xprocessing.XExecutableElement;
import dagger.internal.codegen.xprocessing.XMessager;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;

/**
 * Processing step that verifies that {@code dagger.multibindings.IntoSet}, {@code
 * dagger.multibindings.ElementsIntoSet} and {@code dagger.multibindings.IntoMap} are not present on
 * non-binding methods.
 */
public final class MultibindingAnnotationsProcessingStep
    extends TypeCheckingProcessingStep<XExecutableElement> {
  private final AnyBindingMethodValidator anyBindingMethodValidator;
  private final XMessager messager;

  @Inject
  MultibindingAnnotationsProcessingStep(
      AnyBindingMethodValidator anyBindingMethodValidator, XMessager messager) {
    this.anyBindingMethodValidator = anyBindingMethodValidator;
    this.messager = messager;
  }

  @Override
  public ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(TypeNames.INTO_SET, TypeNames.ELEMENTS_INTO_SET, TypeNames.INTO_MAP);
  }

  @Override
  protected void process(XExecutableElement method, ImmutableSet<ClassName> annotations) {
    if (!anyBindingMethodValidator.isBindingMethod(method)) {
      annotations.forEach(
          annotation ->
              messager.printMessage(
                  ERROR,
                  "Multibinding annotations may only be on @Provides, @Produces, or @Binds methods",
                  method,
                  method.getAnnotation(annotation)));
    }
  }
}
