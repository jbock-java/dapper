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

import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;
import static io.jbock.auto.common.BasicAnnotationProcessor.Step;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.util.ElementFilter.methodsIn;

import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.BindingFactory;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.validation.ModuleValidator;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.validation.XTypeCheckingProcessingStep;
import dagger.internal.codegen.writing.ModuleGenerator;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.auto.common.MoreElements;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * A {@link Step} that validates module classes and generates factories for binding
 * methods.
 */
final class ModuleProcessingStep extends XTypeCheckingProcessingStep<XTypeElement> {
  private final Messager messager;
  private final ModuleValidator moduleValidator;
  private final BindingFactory bindingFactory;
  private final SourceFileGenerator<ProvisionBinding> factoryGenerator;
  private final SourceFileGenerator<TypeElement> moduleConstructorProxyGenerator;
  private final Set<TypeElement> processedModuleElements = new LinkedHashSet<>();

  @Inject
  ModuleProcessingStep(
      Messager messager,
      ModuleValidator moduleValidator,
      BindingFactory bindingFactory,
      SourceFileGenerator<ProvisionBinding> factoryGenerator,
      @ModuleGenerator SourceFileGenerator<TypeElement> moduleConstructorProxyGenerator) {
    this.messager = messager;
    this.moduleValidator = moduleValidator;
    this.bindingFactory = bindingFactory;
    this.factoryGenerator = factoryGenerator;
    this.moduleConstructorProxyGenerator = moduleConstructorProxyGenerator;
  }

  @Override
  public Set<ClassName> annotationClassNames() {
    return Set.of(TypeNames.MODULE);
  }

  @Override
  public Set<XElement> process(
      XProcessingEnv env,
      Map<String, Set<XElement>> elementsByAnnotation) {
    moduleValidator.addKnownModules(
        elementsByAnnotation.values().stream()
            .flatMap(Set::stream)
            .map(XElement::toJavac)
            .map(MoreElements::asType)
            .collect(toList()));
    return super.process(env, elementsByAnnotation);
  }

  @Override
  protected void process(XTypeElement xElement, Set<ClassName> annotations) {
    // TODO(bcorso): Remove conversion to javac type and use XProcessing throughout.
    TypeElement module = xElement.toJavac();
    if (processedModuleElements.contains(module)) {
      return;
    }
    ValidationReport report = moduleValidator.validate(module);
    report.printMessagesTo(messager);
    if (report.isClean()) {
      generateForMethodsIn(module);
    }
    processedModuleElements.add(module);
  }

  private void generateForMethodsIn(TypeElement module) {
    for (ExecutableElement method : methodsIn(module.getEnclosedElements())) {
      if (isAnnotationPresent(method, TypeNames.PROVIDES)) {
        generate(factoryGenerator, bindingFactory.providesMethodBinding(method, module));
      }
    }
    moduleConstructorProxyGenerator.generate(module, messager);
  }

  private <B extends ContributionBinding> void generate(
      SourceFileGenerator<B> generator, B binding) {
    generator.generate(binding, messager);
  }
}
