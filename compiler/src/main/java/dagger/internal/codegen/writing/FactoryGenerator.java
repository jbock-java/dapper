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

package dagger.internal.codegen.writing;

import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedParameters;
import static dagger.internal.codegen.binding.ContributionBinding.FactoryCreationStrategy.DELEGATE;
import static dagger.internal.codegen.binding.ContributionBinding.FactoryCreationStrategy.SINGLETON_INSTANCE;
import static dagger.internal.codegen.binding.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.binding.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.binding.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.binding.SourceFiles.parameterizedGeneratedTypeNameForBinding;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.factoryOf;
import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import dagger.internal.Factory;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.extension.DaggerStreams;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.writing.InjectionMethods.ProvisionMethod;
import dagger.internal.codegen.xprocessing.XFiler;
import dagger.spi.model.BindingKind;
import dagger.spi.model.DependencyRequest;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.FieldSpec;
import io.jbock.javapoet.MethodSpec;
import io.jbock.javapoet.ParameterSpec;
import io.jbock.javapoet.TypeName;
import io.jbock.javapoet.TypeSpec;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;

/**
 * Generates {@link Factory} implementations from {@link ProvisionBinding} instances for {@code
 * Inject} constructors.
 */
public final class FactoryGenerator extends SourceFileGenerator<ProvisionBinding> {
  private final CompilerOptions compilerOptions;

  @Inject
  FactoryGenerator(
      XFiler filer,
      DaggerElements elements,
      CompilerOptions compilerOptions) {
    super(filer, elements);
    this.compilerOptions = compilerOptions;
  }

  @Override
  public Element originatingElement(ProvisionBinding binding) {
    // we only create factories for bindings that have a binding element
    return binding.bindingElement().orElseThrow();
  }

  @Override
  public List<TypeSpec.Builder> topLevelTypes(ProvisionBinding binding) {
    // We don't want to write out resolved bindings -- we want to write out the generic version.
    Preconditions.checkArgument(!binding.unresolved().isPresent());
    Preconditions.checkArgument(binding.bindingElement().isPresent());

    if (binding.factoryCreationStrategy().equals(DELEGATE)) {
      return List.of();
    }

    return List.of(factoryBuilder(binding));
  }

  private TypeSpec.Builder factoryBuilder(ProvisionBinding binding) {
    TypeSpec.Builder factoryBuilder =
        classBuilder(generatedClassNameForBinding(binding))
            .addModifiers(PUBLIC, FINAL)
            .addTypeVariables(bindingTypeElementTypeVariableNames(binding));

    factoryTypeName(binding).ifPresent(factoryBuilder::addSuperinterface);
    addConstructorAndFields(binding, factoryBuilder);
    factoryBuilder.addMethod(getMethod(binding));
    addCreateMethod(binding, factoryBuilder);

    factoryBuilder.addMethod(ProvisionMethod.create(binding, compilerOptions));

    return factoryBuilder;
  }

  private void addConstructorAndFields(ProvisionBinding binding, TypeSpec.Builder factoryBuilder) {
    if (binding.factoryCreationStrategy().equals(SINGLETON_INSTANCE)) {
      return;
    }
    // TODO(bcorso): Make the constructor private?
    MethodSpec.Builder constructor = constructorBuilder().addModifiers(PUBLIC);
    constructorParams(binding).forEach(
        param -> {
          constructor.addParameter(param).addStatement("this.$1N = $1N", param);
          factoryBuilder.addField(
              FieldSpec.builder(param.type, param.name, PRIVATE, FINAL).build());
        });
    factoryBuilder.addMethod(constructor.build());
  }

  private List<ParameterSpec> constructorParams(ProvisionBinding binding) {
    List<ParameterSpec> params = new ArrayList<>();
    moduleParameter(binding).ifPresent(params::add);
    frameworkFields(binding).values().forEach(field -> params.add(toParameter(field)));
    return params;
  }

  private Optional<ParameterSpec> moduleParameter(ProvisionBinding binding) {
    if (binding.requiresModuleInstance()) {
      // TODO(bcorso, dpb): Should this use contributingModule()?
      TypeName type = TypeName.get(binding.bindingTypeElement().get().asType());
      return Optional.of(ParameterSpec.builder(type, "module").build());
    }
    return Optional.empty();
  }

  private Map<DependencyRequest, FieldSpec> frameworkFields(ProvisionBinding binding) {
    UniqueNameSet uniqueFieldNames = new UniqueNameSet();
    // TODO(bcorso, dpb): Add a test for the case when a Factory parameter is named "module".
    moduleParameter(binding).ifPresent(module -> uniqueFieldNames.claim(module.name));
    return Util.transformValues(
        generateBindingFieldsForDependencies(binding),
        field ->
            FieldSpec.builder(
                    field.type(), uniqueFieldNames.getUniqueName(field.name()), PRIVATE, FINAL)
                .build());
  }

  private void addCreateMethod(ProvisionBinding binding, TypeSpec.Builder factoryBuilder) {
    // If constructing a factory for @Inject or @Provides bindings, we use a static create method
    // so that generated components can avoid having to refer to the generic types
    // of the factory.  (Otherwise they may have visibility problems referring to the types.)
    MethodSpec.Builder createMethodBuilder =
        methodBuilder("create")
            .addModifiers(PUBLIC, STATIC)
            .returns(parameterizedGeneratedTypeNameForBinding(binding))
            .addTypeVariables(bindingTypeElementTypeVariableNames(binding));

    switch (binding.factoryCreationStrategy()) {
      case SINGLETON_INSTANCE:
        FieldSpec.Builder instanceFieldBuilder =
            FieldSpec.builder(
                    generatedClassNameForBinding(binding), "INSTANCE", PRIVATE, STATIC, FINAL)
                .initializer("new $T()", generatedClassNameForBinding(binding));

        if (!bindingTypeElementTypeVariableNames(binding).isEmpty()) {
          // If the factory has type parameters, ignore them in the field declaration & initializer
          instanceFieldBuilder.addAnnotation(suppressWarnings(RAWTYPES));
          createMethodBuilder.addAnnotation(suppressWarnings(UNCHECKED));
        }

        ClassName instanceHolderName =
            generatedClassNameForBinding(binding).nestedClass("InstanceHolder");
        createMethodBuilder.addStatement("return $T.INSTANCE", instanceHolderName);
        factoryBuilder.addType(
            TypeSpec.classBuilder(instanceHolderName)
                .addModifiers(PRIVATE, STATIC, FINAL)
                .addField(instanceFieldBuilder.build())
                .build());
        break;
      case CLASS_CONSTRUCTOR:
        List<ParameterSpec> params = constructorParams(binding);
        createMethodBuilder.addParameters(params);
        createMethodBuilder.addStatement(
            "return new $T($L)",
            parameterizedGeneratedTypeNameForBinding(binding),
            makeParametersCodeBlock(params.stream()
                .map(input -> CodeBlock.of("$N", input))
                .collect(Collectors.toList())));
        break;
      default:
        throw new AssertionError();
    }
    factoryBuilder.addMethod(createMethodBuilder.build());
  }

  private MethodSpec getMethod(ProvisionBinding binding) {
    UniqueNameSet uniqueFieldNames = new UniqueNameSet();
    Map<DependencyRequest, FieldSpec> frameworkFields = frameworkFields(binding);
    frameworkFields.values().forEach(field -> uniqueFieldNames.claim(field.name));
    Map<VariableElement, ParameterSpec> assistedParameters =
        assistedParameters(binding).stream()
            .collect(
                DaggerStreams.toImmutableMap(
                    element -> element,
                    element ->
                        ParameterSpec.builder(
                                TypeName.get(element.asType()),
                                uniqueFieldNames.getUniqueName(element.getSimpleName()))
                            .build()));
    TypeName providedTypeName = providedTypeName(binding);
    MethodSpec.Builder getMethod =
        methodBuilder("get")
            .addModifiers(PUBLIC)
            .returns(providedTypeName)
            .addParameters(assistedParameters.values());

    if (factoryTypeName(binding).isPresent()) {
      getMethod.addAnnotation(Override.class);
    }

    CodeBlock invokeNewInstance =
        ProvisionMethod.invoke(
            binding,
            request ->
                frameworkTypeUsageStatement(
                    CodeBlock.of("$N", frameworkFields.get(request)), request.kind()),
            param -> assistedParameters.get(param).name,
            generatedClassNameForBinding(binding),
            moduleParameter(binding).map(module -> CodeBlock.of("$N", module)),
            compilerOptions
        );

    getMethod.addStatement("return $L", invokeNewInstance);
    return getMethod.build();
  }

  private static TypeName providedTypeName(ProvisionBinding binding) {
    return TypeName.get(binding.contributedType());
  }

  private static Optional<TypeName> factoryTypeName(ProvisionBinding binding) {
    return binding.kind() == BindingKind.ASSISTED_INJECTION
        ? Optional.empty()
        : Optional.of(factoryOf(providedTypeName(binding)));
  }

  private static ParameterSpec toParameter(FieldSpec field) {
    return ParameterSpec.builder(field.type, field.name).build();
  }
}
