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

import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XExecutableElement;
import dagger.internal.codegen.xprocessing.XVariableElement;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;

/**
 * Processing step that validates that the {@code BindsInstance} annotation is applied to the
 * correct elements.
 */
public final class BindsInstanceProcessingStep extends XTypeCheckingProcessingStep<XElement> {
  private final BindsInstanceMethodValidator methodValidator;
  private final BindsInstanceParameterValidator parameterValidator;
  private final Messager messager;

  @Inject
  BindsInstanceProcessingStep(
      BindsInstanceMethodValidator methodValidator,
      BindsInstanceParameterValidator parameterValidator,
      Messager messager) {
    this.methodValidator = methodValidator;
    this.parameterValidator = parameterValidator;
    this.messager = messager;
  }

  @Override
  public Set<ClassName> annotationClassNames() {
    return Set.of(TypeNames.BINDS_INSTANCE);
  }

  @Override
  protected void process(XElement xElement, Set<ClassName> annotations) {
    Element element = xElement.toJavac();
    switch (element.getKind()) {
      case PARAMETER:
        parameterValidator.validate((XVariableElement) xElement).printMessagesTo(messager);
        break;
      case METHOD:
        methodValidator.validate((XExecutableElement) xElement).printMessagesTo(messager);
        break;
      default:
        throw new AssertionError(xElement);
    }
  }
}
