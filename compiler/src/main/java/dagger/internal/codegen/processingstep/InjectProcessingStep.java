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

package dagger.internal.codegen.processingstep;

import static dagger.internal.codegen.xprocessing.XElement.isConstructor;
import static dagger.internal.codegen.xprocessing.XElement.isField;
import static dagger.internal.codegen.xprocessing.XElement.isMethod;
import static dagger.internal.codegen.xprocessing.XElements.asConstructor;
import static dagger.internal.codegen.xprocessing.XElements.asField;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;

import dagger.internal.codegen.binding.InjectBindingRegistry;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.collect.Sets;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XElement;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.Set;

/**
 * An annotation processor for generating Dagger implementation code based on the {@code Inject}
 * annotation.
 */
// TODO(gak): add some error handling for bad source files
// TODO(bcorso): Add support in TypeCheckingProcessingStep to perform custom validation and use
// SuperficialInjectValidator rather than SuperficialValidator.
final class InjectProcessingStep extends TypeCheckingProcessingStep<XElement> {
  private final InjectBindingRegistry injectBindingRegistry;
  private final Set<XElement> processedElements = Sets.newHashSet();

  @Inject
  InjectProcessingStep(InjectBindingRegistry injectBindingRegistry) {
    this.injectBindingRegistry = injectBindingRegistry;
  }

  @Override
  public ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(TypeNames.INJECT, TypeNames.ASSISTED_INJECT);
  }

  @Override
  protected void process(XElement injectElement, ImmutableSet<ClassName> annotations) {
    // Only process an element once to avoid getting duplicate errors when an element is annotated
    // with multiple inject annotations.
    if (processedElements.contains(injectElement)) {
      return;
    }

    if (isConstructor(injectElement)) {
      injectBindingRegistry.tryRegisterInjectConstructor(asConstructor(injectElement));
    } else if (isField(injectElement)) {
      injectBindingRegistry.tryRegisterInjectField(asField(injectElement));
    } else if (isMethod(injectElement)) {
      injectBindingRegistry.tryRegisterInjectMethod(asMethod(injectElement));
    }

    processedElements.add(injectElement);
  }
}
