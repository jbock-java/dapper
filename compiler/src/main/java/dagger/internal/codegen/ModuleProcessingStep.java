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

import static com.google.auto.common.BasicAnnotationProcessor.Step;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.lang.model.util.ElementFilter.typesIn;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.BindingFactory;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.DelegateDeclaration;
import dagger.internal.codegen.binding.DelegateDeclaration.Factory;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.validation.ModuleValidator;
import dagger.internal.codegen.validation.TypeCheckingProcessingStep;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.writing.InaccessibleMapKeyProxyGenerator;
import dagger.internal.codegen.writing.ModuleGenerator;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * A {@link Step} that validates module classes and generates factories for binding
 * methods.
 */
final class ModuleProcessingStep extends TypeCheckingProcessingStep<TypeElement> {
  private final Messager messager;
  private final ModuleValidator moduleValidator;
  private final BindingFactory bindingFactory;
  private final SourceFileGenerator<ProvisionBinding> factoryGenerator;
  private final SourceFileGenerator<TypeElement> moduleConstructorProxyGenerator;
  private final InaccessibleMapKeyProxyGenerator inaccessibleMapKeyProxyGenerator;
  private final DelegateDeclaration.Factory delegateDeclarationFactory;
  private final Set<TypeElement> processedModuleElements = Sets.newLinkedHashSet();

  @Inject
  ModuleProcessingStep(
      Messager messager,
      ModuleValidator moduleValidator,
      BindingFactory bindingFactory,
      SourceFileGenerator<ProvisionBinding> factoryGenerator,
      @ModuleGenerator SourceFileGenerator<TypeElement> moduleConstructorProxyGenerator,
      InaccessibleMapKeyProxyGenerator inaccessibleMapKeyProxyGenerator,
      Factory delegateDeclarationFactory) {
    super(MoreElements::asType);
    this.messager = messager;
    this.moduleValidator = moduleValidator;
    this.bindingFactory = bindingFactory;
    this.factoryGenerator = factoryGenerator;
    this.moduleConstructorProxyGenerator = moduleConstructorProxyGenerator;
    this.inaccessibleMapKeyProxyGenerator = inaccessibleMapKeyProxyGenerator;
    this.delegateDeclarationFactory = delegateDeclarationFactory;
  }

  @Override
  public Set<ClassName> annotationClassNames() {
    return ImmutableSet.of(TypeNames.MODULE);
  }

  @Override
  public Set<Element> process(Map<String, Set<Element>> elementsByAnnotation) {
    List<TypeElement> modules = typesIn(elementsByAnnotation.values().stream()
        .flatMap(Set::stream)
        .collect(toList()));
    moduleValidator.addKnownModules(modules);
    return super.process(elementsByAnnotation);
  }

  @Override
  protected void process(TypeElement module, Set<ClassName> annotations) {
    if (processedModuleElements.contains(module)) {
      return;
    }
    ValidationReport<TypeElement> report = moduleValidator.validate(module);
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
      } else if (isAnnotationPresent(method, TypeNames.BINDS)) {
        inaccessibleMapKeyProxyGenerator.generate(bindsMethodBinding(module, method), messager);
      }
    }
    moduleConstructorProxyGenerator.generate(module, messager);
  }

  private <B extends ContributionBinding> void generate(
      SourceFileGenerator<B> generator, B binding) {
    generator.generate(binding, messager);
    inaccessibleMapKeyProxyGenerator.generate(binding, messager);
  }

  private ContributionBinding bindsMethodBinding(TypeElement module, ExecutableElement method) {
    return bindingFactory.unresolvedDelegateBinding(
        delegateDeclarationFactory.create(method, module));
  }
}
