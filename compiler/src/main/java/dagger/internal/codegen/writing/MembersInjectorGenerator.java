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

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedInjectedConstructors;
import static dagger.internal.codegen.binding.InjectionAnnotations.injectedConstructors;
import static dagger.internal.codegen.binding.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.binding.SourceFiles.frameworkFieldUsages;
import static dagger.internal.codegen.binding.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.binding.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.binding.SourceFiles.parameterizedGeneratedTypeNameForBinding;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.membersInjectorOf;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.MembersInjector;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.FrameworkField;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.writing.InjectionMethods.InjectionSiteMethod;
import dagger.model.DependencyRequest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;

/**
 * Generates {@link MembersInjector} implementations from {@link MembersInjectionBinding} instances.
 */
public final class MembersInjectorGenerator extends SourceFileGenerator<MembersInjectionBinding> {
  private final DaggerTypes types;

  @Inject
  MembersInjectorGenerator(
      Filer filer,
      DaggerElements elements,
      DaggerTypes types) {
    super(filer, elements);
    this.types = types;
  }

  @Override
  public Element originatingElement(MembersInjectionBinding binding) {
    return binding.membersInjectedType();
  }

  @Override
  public List<TypeSpec.Builder> topLevelTypes(MembersInjectionBinding binding) {
    // Empty members injection bindings are special and don't need source files.
    if (binding.injectionSites().isEmpty()) {
      return List.of();
    }

    // Members injectors for classes with no local injection sites and no @Inject
    // constructor are unused.
    if (!binding.hasLocalInjectionSites()
        && injectedConstructors(binding.membersInjectedType()).isEmpty()
        && assistedInjectedConstructors(binding.membersInjectedType()).isEmpty()) {
      return List.of();
    }


    // We don't want to write out resolved bindings -- we want to write out the generic version.
    Preconditions.checkState(
        binding.unresolved().isEmpty(),
        "tried to generate a MembersInjector for a binding of a resolved generic type: %s",
        binding);

    ClassName generatedTypeName = membersInjectorNameForType(binding.membersInjectedType());
    List<TypeVariableName> typeParameters = bindingTypeElementTypeVariableNames(binding);
    TypeSpec.Builder injectorTypeBuilder =
        classBuilder(generatedTypeName)
            .addModifiers(PUBLIC, FINAL)
            .addTypeVariables(typeParameters);

    TypeName injectedTypeName = TypeName.get(binding.key().type());
    TypeName implementedType = membersInjectorOf(injectedTypeName);
    injectorTypeBuilder.addSuperinterface(implementedType);

    MethodSpec.Builder injectMembersBuilder =
        methodBuilder("injectMembers")
            .addModifiers(PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(injectedTypeName, "instance");

    Map<DependencyRequest, FrameworkField> fields =
        generateBindingFieldsForDependencies(binding);

    Map<DependencyRequest, FieldSpec> dependencyFields =
        new LinkedHashMap<>();

    MethodSpec.Builder constructorBuilder = constructorBuilder().addModifiers(PUBLIC);

    // We use a static create method so that generated components can avoid having
    // to refer to the generic types of the factory.
    // (Otherwise they may have visibility problems referring to the types.)
    MethodSpec.Builder createMethodBuilder =
        methodBuilder("create")
            .returns(implementedType)
            .addModifiers(PUBLIC, STATIC)
            .addTypeVariables(typeParameters);

    createMethodBuilder.addCode(
        "return new $T(", parameterizedGeneratedTypeNameForBinding(binding));
    List<CodeBlock> constructorInvocationParameters = new ArrayList<>();

    boolean usesRawFrameworkTypes = false;
    UniqueNameSet fieldNames = new UniqueNameSet();
    for (Entry<DependencyRequest, FrameworkField> fieldEntry : fields.entrySet()) {
      DependencyRequest dependency = fieldEntry.getKey();
      FrameworkField bindingField = fieldEntry.getValue();

      // If the dependency type is not visible to this members injector, then use the raw framework
      // type for the field.
      boolean useRawFrameworkType =
          !isTypeAccessibleFrom(dependency.key().type(), generatedTypeName.packageName());

      String fieldName = fieldNames.getUniqueName(bindingField.name());
      TypeName fieldType = useRawFrameworkType ? bindingField.type().rawType : bindingField.type();
      FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, fieldName, PRIVATE, FINAL);
      ParameterSpec.Builder parameterBuilder = ParameterSpec.builder(fieldType, fieldName);

      // If we're using the raw type for the field, then suppress the injectMembers method's
      // unchecked-type warning and the field's and the constructor and create-method's
      // parameters' raw-type warnings.
      if (useRawFrameworkType) {
        usesRawFrameworkTypes = true;
        fieldBuilder.addAnnotation(suppressWarnings(RAWTYPES));
        parameterBuilder.addAnnotation(suppressWarnings(RAWTYPES));
      }
      constructorBuilder.addParameter(parameterBuilder.build());
      createMethodBuilder.addParameter(parameterBuilder.build());

      FieldSpec field = fieldBuilder.build();
      injectorTypeBuilder.addField(field);
      constructorBuilder.addStatement("this.$1N = $1N", field);
      dependencyFields.put(dependency, field);
      constructorInvocationParameters.add(CodeBlock.of("$N", field));
    }

    createMethodBuilder.addCode(
        constructorInvocationParameters.stream().collect(toParametersCodeBlock()));
    createMethodBuilder.addCode(");");

    injectorTypeBuilder.addMethod(constructorBuilder.build());
    injectorTypeBuilder.addMethod(createMethodBuilder.build());

    injectMembersBuilder.addCode(
        InjectionSiteMethod.invokeAll(
            binding.injectionSites(),
            generatedTypeName,
            CodeBlock.of("instance"),
            binding.key().type(),
            frameworkFieldUsages(binding.dependencies(), dependencyFields)::get,
            types
        ));

    if (usesRawFrameworkTypes) {
      injectMembersBuilder.addAnnotation(suppressWarnings(UNCHECKED));
    }
    injectorTypeBuilder.addMethod(injectMembersBuilder.build());

    for (InjectionSite injectionSite : binding.injectionSites()) {
      if (injectionSite.element().getEnclosingElement().equals(binding.membersInjectedType())) {
        injectorTypeBuilder.addMethod(InjectionSiteMethod.create(injectionSite));
      }
    }

    return List.of(injectorTypeBuilder);
  }
}
