/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static dagger.internal.codegen.binding.SourceFiles.simpleVariableName;
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeSpecs.addSupertype;
import static dagger.internal.codegen.langmodel.Accessibility.isElementAccessibleFrom;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.binding.ComponentCreatorDescriptor;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ComponentRequirement.NullPolicy;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.xprocessing.MethodSpecs;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XVariableElement;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.FieldSpec;
import io.jbock.javapoet.MethodSpec;
import io.jbock.javapoet.ParameterSpec;
import io.jbock.javapoet.TypeName;
import io.jbock.javapoet.TypeSpec;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;

/** Factory for creating {@link ComponentCreatorImplementation} instances. */
final class ComponentCreatorImplementationFactory {

  private final ComponentImplementation componentImplementation;
  private final ModuleProxies moduleProxies;

  @Inject
  ComponentCreatorImplementationFactory(
      ComponentImplementation componentImplementation,
      ModuleProxies moduleProxies) {
    this.componentImplementation = componentImplementation;
    this.moduleProxies = moduleProxies;
  }

  /** Returns a new creator implementation for the given component, if necessary. */
  Optional<ComponentCreatorImplementation> create() {
    if (!componentImplementation.componentDescriptor().hasCreator()) {
      return Optional.empty();
    }

    Optional<ComponentCreatorDescriptor> creatorDescriptor =
        componentImplementation.componentDescriptor().creatorDescriptor();

    Builder builder =
        creatorDescriptor.isPresent()
            ? new BuilderForCreatorDescriptor(creatorDescriptor.get())
            : new BuilderForGeneratedRootComponentBuilder();
    return Optional.of(builder.build());
  }

  /** Base class for building a creator implementation. */
  private abstract class Builder {
    private final TypeSpec.Builder classBuilder =
        classBuilder(componentImplementation.getCreatorName());
    private final UniqueNameSet fieldNames = new UniqueNameSet();
    private Map<ComponentRequirement, FieldSpec> fields;

    /** Builds the {@link ComponentCreatorImplementation}. */
    ComponentCreatorImplementation build() {
      setModifiers();
      setSupertype();
      addConstructor();
      this.fields = addFields();
      addSetterMethods();
      addFactoryMethod();
      return ComponentCreatorImplementation.create(
          classBuilder.build(), componentImplementation.getCreatorName(), fields);
    }

    /** Returns the descriptor for the component. */
    final ComponentDescriptor componentDescriptor() {
      return componentImplementation.componentDescriptor();
    }

    /**
     * The set of requirements that must be passed to the component's constructor in the order
     * they must be passed.
     */
    final Set<ComponentRequirement> componentConstructorRequirements() {
      return componentImplementation.graph().componentRequirements();
    }

    /** Returns the requirements that have setter methods on the creator type. */
    abstract Set<ComponentRequirement> setterMethods();

    /**
     * Returns the component requirements that have factory method parameters, mapped to the name
     * for that parameter.
     */
    abstract Map<ComponentRequirement, String> factoryMethodParameters();

    /**
     * The {@link ComponentRequirement}s that this creator allows users to set. Values are a status
     * for each requirement indicating what's needed for that requirement in the implementation
     * class currently being generated.
     */
    abstract Map<ComponentRequirement, RequirementStatus> userSettableRequirements();

    /**
     * Component requirements that are both settable by the creator and needed to construct the
     * component.
     */
    private Set<ComponentRequirement> neededUserSettableRequirements() {
      return Util.intersection(
          userSettableRequirements().keySet(), componentConstructorRequirements());
    }

    private void setModifiers() {
      visibility().ifPresent(classBuilder::addModifiers);
      classBuilder.addModifiers(STATIC, FINAL);
    }

    /** Returns the visibility modifier the generated class should have, if any. */
    protected abstract Optional<Modifier> visibility();

    /** Sets the superclass being extended or interface being implemented for this creator. */
    protected abstract void setSupertype();

    /** Adds a constructor for the creator type, if needed. */
    protected void addConstructor() {
      MethodSpec.Builder constructor = MethodSpec.constructorBuilder().addModifiers(PRIVATE);
      componentImplementation
          .creatorComponentFields()
          .forEach(
              field -> {
                fieldNames.claim(field.name);
                classBuilder.addField(field);
                constructor.addParameter(field.type, field.name);
                constructor.addStatement("this.$1N = $1N", field);
              });
      classBuilder.addMethod(constructor.build());
    }

    private Map<ComponentRequirement, FieldSpec> addFields() {
      // Fields in an abstract creator class need to be visible from subclasses.
      Map<ComponentRequirement, FieldSpec> result =
          Util.toMap(
              Util.intersection(neededUserSettableRequirements(), setterMethods()),
              requirement ->
                  FieldSpec.builder(
                          requirement.type().getTypeName(),
                          fieldNames.getUniqueName(requirement.variableName()),
                          PRIVATE)
                      .build());
      classBuilder.addFields(result.values());
      return result;
    }

    private void addSetterMethods() {
      Set<ComponentRequirement> setterMethods = setterMethods();
      userSettableRequirements().entrySet().stream().filter(e -> setterMethods.contains(e.getKey()))
          .forEach(e -> {
            ComponentRequirement requirement = e.getKey();
            RequirementStatus status = e.getValue();
            createSetterMethod(requirement, status).ifPresent(classBuilder::addMethod);
          });
    }

    /** Creates a new setter method builder, with no method body, for the given requirement. */
    protected abstract MethodSpec.Builder setterMethodBuilder(ComponentRequirement requirement);

    private Optional<MethodSpec> createSetterMethod(
        ComponentRequirement requirement, RequirementStatus status) {
      switch (status) {
        case NEEDED:
          return Optional.of(normalSetterMethod(requirement));
        case UNNEEDED:
          // TODO(bcorso): Don't generate noop setters for any unneeded requirements.
          // However, since this is a breaking change we can at least avoid trying
          // to generate noop setters for impossible cases like when the requirement type
          // is in another package. This avoids unnecessary breakages in Dagger's generated
          // due to the noop setters.
          if (isElementAccessibleFrom(
              requirement.typeElement(), componentImplementation.name().packageName())) {
            return Optional.of(noopSetterMethod(requirement));
          } else {
            return Optional.empty();
          }
        case UNSETTABLE_REPEATED_MODULE:
          return Optional.of(repeatedModuleSetterMethod(requirement));
      }
      throw new AssertionError();
    }

    private MethodSpec normalSetterMethod(ComponentRequirement requirement) {
      MethodSpec.Builder method = setterMethodBuilder(requirement);
      ParameterSpec parameter = parameter(method.build());
      method.addStatement(
          "this.$N = $L",
          fields.get(requirement),
          requirement.nullPolicy().equals(NullPolicy.ALLOW)
              ? CodeBlock.of("$N", parameter)
              : CodeBlock.of("$T.checkNotNull($N)", dagger.internal.Preconditions.class, parameter));
      return maybeReturnThis(method);
    }

    private MethodSpec noopSetterMethod(ComponentRequirement requirement) {
      MethodSpec.Builder method = setterMethodBuilder(requirement);
      ParameterSpec parameter = parameter(method.build());
      method
          .addAnnotation(Deprecated.class)
          .addJavadoc(
              "@deprecated This module is declared, but an instance is not used in the component. "
                  + "This method is a no-op. For more, see https://dagger.dev/unused-modules.\n")
          .addStatement("$T.checkNotNull($N)", dagger.internal.Preconditions.class, parameter);
      return maybeReturnThis(method);
    }

    private MethodSpec repeatedModuleSetterMethod(ComponentRequirement requirement) {
      return setterMethodBuilder(requirement)
          .addStatement(
              "throw new $T($T.format($S, $T.class.getCanonicalName()))",
              UnsupportedOperationException.class,
              String.class,
              "%s cannot be set because it is inherited from the enclosing component",
              TypeNames.rawTypeName(requirement.type().getTypeName()))
          .build();
    }

    private ParameterSpec parameter(MethodSpec method) {
      return Util.getOnlyElement(method.parameters);
    }

    private MethodSpec maybeReturnThis(MethodSpec.Builder method) {
      MethodSpec built = method.build();
      return built.returnType.equals(TypeName.VOID)
          ? built
          : method.addStatement("return this").build();
    }

    private void addFactoryMethod() {
      classBuilder.addMethod(factoryMethod());
    }

    MethodSpec factoryMethod() {
      MethodSpec.Builder factoryMethod = factoryMethodBuilder();
      factoryMethod
          .returns(componentDescriptor().typeElement().getClassName())
          .addModifiers(PUBLIC);

      Map<ComponentRequirement, String> factoryMethodParameters =
          factoryMethodParameters();
      userSettableRequirements()
          .keySet()
          .forEach(
              requirement -> {
                if (fields.containsKey(requirement)) {
                  FieldSpec field = fields.get(requirement);
                  addNullHandlingForField(requirement, field, factoryMethod);
                } else if (factoryMethodParameters.containsKey(requirement)) {
                  String parameterName = factoryMethodParameters.get(requirement);
                  addNullHandlingForParameter(requirement, parameterName, factoryMethod);
                }
              });
      factoryMethod.addStatement(
          "return new $T($L)",
          componentImplementation.name(),
          componentConstructorArgs(factoryMethodParameters));
      return factoryMethod.build();
    }

    private void addNullHandlingForField(
        ComponentRequirement requirement, FieldSpec field, MethodSpec.Builder factoryMethod) {
      switch (requirement.nullPolicy()) {
        case NEW:
          Preconditions.checkState(requirement.kind().isModule());
          factoryMethod
              .beginControlFlow("if ($N == null)", field)
              .addStatement("this.$N = $L", field, newModuleInstance(requirement))
              .endControlFlow();
          break;
        case THROW:
          // TODO(cgdecker,ronshapiro): ideally this should use the key instead of a class for
          // @BindsInstance requirements, but that's not easily proguardable.
          factoryMethod.addStatement(
              "$T.checkBuilderRequirement($N, $T.class)",
              dagger.internal.Preconditions.class,
              field,
              TypeNames.rawTypeName(field.type));
          break;
        case ALLOW:
          break;
      }
    }

    private void addNullHandlingForParameter(
        ComponentRequirement requirement, String parameter, MethodSpec.Builder factoryMethod) {
      if (!requirement.nullPolicy().equals(NullPolicy.ALLOW)) {
        // Factory method parameters are always required unless they are a nullable
        // binds-instance (i.e. ALLOW)
        factoryMethod.addStatement("$T.checkNotNull($L)", dagger.internal.Preconditions.class, parameter);
      }
    }

    /** Returns a builder for the creator's factory method. */
    protected abstract MethodSpec.Builder factoryMethodBuilder();

    private CodeBlock componentConstructorArgs(
        Map<ComponentRequirement, String> factoryMethodParameters) {
      return Stream.concat(
              componentImplementation.creatorComponentFields().stream()
                  .map(field -> CodeBlock.of("$N", field)),
              componentConstructorRequirements().stream()
                  .map(
                      requirement -> {
                        if (fields.containsKey(requirement)) {
                          return CodeBlock.of("$N", fields.get(requirement));
                        } else if (factoryMethodParameters.containsKey(requirement)) {
                          return CodeBlock.of("$L", factoryMethodParameters.get(requirement));
                        } else {
                          return newModuleInstance(requirement);
                        }
                      }))
          .collect(toParametersCodeBlock());
    }

    private CodeBlock newModuleInstance(ComponentRequirement requirement) {
      Preconditions.checkArgument(requirement.kind().isModule()); // this should be guaranteed to be true here
      return moduleProxies.newModuleInstance(
          requirement.typeElement(), componentImplementation.getCreatorName());
    }
  }

  /** Builder for a creator type defined by a {@code ComponentCreatorDescriptor}. */
  private final class BuilderForCreatorDescriptor extends Builder {
    final ComponentCreatorDescriptor creatorDescriptor;

    BuilderForCreatorDescriptor(ComponentCreatorDescriptor creatorDescriptor) {
      this.creatorDescriptor = creatorDescriptor;
    }

    @Override
    protected Map<ComponentRequirement, RequirementStatus> userSettableRequirements() {
      return Util.toMap(creatorDescriptor.userSettableRequirements(), this::requirementStatus);
    }

    @Override
    protected Optional<Modifier> visibility() {
      return Optional.of(PRIVATE);
    }

    @Override
    protected void setSupertype() {
      addSupertype(super.classBuilder, creatorDescriptor.typeElement());
    }

    @Override
    protected void addConstructor() {
      if (!componentImplementation.creatorComponentFields().isEmpty()) {
        super.addConstructor();
      }
    }

    @Override
    protected Set<ComponentRequirement> setterMethods() {
      return new LinkedHashSet<>(creatorDescriptor.setterMethods().keySet());
    }

    @Override
    protected Map<ComponentRequirement, String> factoryMethodParameters() {
      return new LinkedHashMap<>(
          Util.transformValues(
              creatorDescriptor.factoryParameters(),
              XVariableElement::getName));
    }

    private XType creatorType() {
      return creatorDescriptor.typeElement().getType();
    }

    @Override
    protected MethodSpec.Builder factoryMethodBuilder() {
      return MethodSpecs.overriding(creatorDescriptor.factoryMethod(), creatorType());
    }

    private RequirementStatus requirementStatus(ComponentRequirement requirement) {
      if (isRepeatedModule(requirement)) {
        return RequirementStatus.UNSETTABLE_REPEATED_MODULE;
      }

      return componentConstructorRequirements().contains(requirement)
          ? RequirementStatus.NEEDED
          : RequirementStatus.UNNEEDED;
    }

    /**
     * Returns whether the given requirement is for a repeat of a module inherited from an ancestor
     * component. This creator is not allowed to set such a module.
     */
    boolean isRepeatedModule(ComponentRequirement requirement) {
      return !componentConstructorRequirements().contains(requirement)
          && !isOwnedModule(requirement);
    }

    /**
     * Returns whether the given {@code requirement} is for a module type owned by the component.
     */
    private boolean isOwnedModule(ComponentRequirement requirement) {
      return componentImplementation
          .graph()
          .ownedModuleTypes()
          .contains(toJavac(requirement.typeElement()));
    }

    @Override
    protected MethodSpec.Builder setterMethodBuilder(ComponentRequirement requirement) {
      XMethodElement supertypeMethod = creatorDescriptor.setterMethods().get(requirement);
      MethodSpec.Builder method = MethodSpecs.overriding(supertypeMethod, creatorType());
      if (!supertypeMethod.getReturnType().isVoid()) {
        // Take advantage of covariant returns so that we don't have to worry about type variables
        method.returns(componentImplementation.getCreatorName());
      }
      return method;
    }
  }

  /**
   * Builder for a component builder class that is automatically generated for a root component that
   * does not have its own user-defined creator type (i.e. a {@code ComponentCreatorDescriptor}).
   */
  private final class BuilderForGeneratedRootComponentBuilder extends Builder {

    @Override
    protected Map<ComponentRequirement, RequirementStatus> userSettableRequirements() {
      return Util.toMap(
          setterMethods(),
          requirement ->
              componentConstructorRequirements().contains(requirement)
                  ? RequirementStatus.NEEDED
                  : RequirementStatus.UNNEEDED);
    }

    @Override
    protected Optional<Modifier> visibility() {
      return componentImplementation.componentDescriptor().typeElement().isPublic()
          ? Optional.of(PUBLIC)
          : Optional.empty();
    }

    @Override
    protected void setSupertype() {
      // There's never a supertype for a root component auto-generated builder type.
    }

    @Override
    protected Set<ComponentRequirement> setterMethods() {
      return componentDescriptor().dependenciesAndConcreteModules();
    }

    @Override
    protected Map<ComponentRequirement, String> factoryMethodParameters() {
      return Map.of();
    }

    @Override
    protected MethodSpec.Builder factoryMethodBuilder() {
      return methodBuilder("build");
    }

    @Override
    protected MethodSpec.Builder setterMethodBuilder(ComponentRequirement requirement) {
      String name = simpleVariableName(requirement.typeElement().getClassName());
      return methodBuilder(name)
          .addModifiers(PUBLIC)
          .addParameter(requirement.type().getTypeName(), name)
          .returns(componentImplementation.getCreatorName());
    }
  }

  /** Enumeration of statuses a component requirement may have in a creator. */
  enum RequirementStatus {
    /** An instance is needed to create the component. */
    NEEDED,

    /**
     * An instance is not needed to create the component, but the requirement is for a module owned
     * by the component. Setting the requirement is a no-op and any setter method should be marked
     * deprecated on the generated type as a warning to the user.
     */
    UNNEEDED,

    /**
     * The requirement may not be set in this creator because the module it is for is already
     * inherited from an ancestor component. Any setter method for it should throw an exception.
     */
    UNSETTABLE_REPEATED_MODULE,
    ;
  }
}
