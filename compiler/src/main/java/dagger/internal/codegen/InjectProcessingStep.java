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

package dagger.internal.codegen;

import dagger.internal.codegen.binding.InjectBindingRegistry;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.validation.EnclosingTypeElementValidator;
import dagger.internal.codegen.validation.TypeCheckingProcessingStep;
import dagger.internal.codegen.xprocessing.XElement;
import io.jbock.auto.common.MoreElements;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor8;

/**
 * An annotation processor for generating Dagger implementation code based on the {@code Inject}
 * annotation.
 */
// TODO(gak): add some error handling for bad source files
final class InjectProcessingStep extends TypeCheckingProcessingStep<XElement> {
  private final ElementVisitor<Void, Void> visitor;
  private final Set<Element> processedElements = new LinkedHashSet<>();

  @Inject
  InjectProcessingStep(
      EnclosingTypeElementValidator elementValidator,
      InjectBindingRegistry injectBindingRegistry) {
    super(elementValidator);
    this.visitor =
        new ElementKindVisitor8<>() {
          @Override
          public Void visitExecutableAsConstructor(
              ExecutableElement constructorElement, Void aVoid) {
            injectBindingRegistry.tryRegisterConstructor(constructorElement);
            return null;
          }

          @Override
          public Void visitVariableAsField(VariableElement fieldElement, Void aVoid) {
            injectBindingRegistry.tryRegisterMembersInjectedType(
                MoreElements.asType(fieldElement.getEnclosingElement()));
            return null;
          }

          @Override
          public Void visitExecutableAsMethod(ExecutableElement methodElement, Void aVoid) {
            injectBindingRegistry.tryRegisterMembersInjectedType(
                MoreElements.asType(methodElement.getEnclosingElement()));
            return null;
          }
        };
  }

  @Override
  public Set<ClassName> annotationClassNames() {
    return new LinkedHashSet<>(List.of(TypeNames.INJECT, TypeNames.ASSISTED_INJECT));
  }

  @Override
  protected void process(XElement xElement, Set<ClassName> annotations) {
    // TODO(bcorso): Remove conversion to javac type and use XProcessing throughout.
    Element injectElement = xElement.toJavac();
    // Only process an element once to avoid getting duplicate errors when an element is annotated
    // with multiple inject annotations.
    if (processedElements.contains(injectElement)) {
      return;
    }

    injectElement.accept(visitor, null);

    processedElements.add(injectElement);
  }
}
