/*
 * Copyright (C) 2022 The Dagger Authors.
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

import static dagger.internal.codegen.writing.ComponentNames.getTopLevelClassName;
import static io.jbock.javapoet.MethodSpec.constructorBuilder;
import static io.jbock.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.collect.ListMultimap;
import dagger.internal.codegen.collect.MultimapBuilder;
import dagger.internal.codegen.writing.ComponentImplementation.FieldSpecKind;
import dagger.internal.codegen.writing.ComponentImplementation.MethodSpecKind;
import dagger.internal.codegen.writing.ComponentImplementation.TypeSpecKind;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.FieldSpec;
import io.jbock.javapoet.MethodSpec;
import io.jbock.javapoet.TypeSpec;
import jakarta.inject.Inject;

/** Represents the implementation of the generated holder for the components. */
@PerGeneratedFile
public final class ComponentWrapperImplementation implements GeneratedImplementation {
  private final BindingGraph graph;
  private final ClassName name;
  private final UniqueNameSet typeNames = new UniqueNameSet();
  private final UniqueNameSet fieldNames = new UniqueNameSet();
  private final UniqueNameSet methodNames = new UniqueNameSet();
  private final ListMultimap<FieldSpecKind, FieldSpec> fieldSpecsMap =
      MultimapBuilder.enumKeys(FieldSpecKind.class).arrayListValues().build();
  private final ListMultimap<MethodSpecKind, MethodSpec> methodSpecsMap =
      MultimapBuilder.enumKeys(MethodSpecKind.class).arrayListValues().build();
  private final ListMultimap<TypeSpecKind, TypeSpec> typeSpecsMap =
      MultimapBuilder.enumKeys(TypeSpecKind.class).arrayListValues().build();

  @Inject
  ComponentWrapperImplementation(@TopLevel BindingGraph graph) {
    this.graph = graph;
    this.name = ComponentNames.getTopLevelClassName(graph.componentDescriptor());
  }

  @Override
  public ClassName name() {
    return name;
  }

  @Override
  public String getUniqueClassName(String name) {
    return typeNames.getUniqueName(name);
  }

  @Override
  public String getUniqueFieldName(String name) {
    return fieldNames.getUniqueName(name);
  }

  @Override
  public String getUniqueMethodName(String name) {
    return methodNames.getUniqueName(name);
  }

  @Override
  public void claimMethodName(String name) {
    methodNames.claim(name);
  }

  @Override
  public void addField(FieldSpecKind fieldKind, FieldSpec fieldSpec) {
    fieldSpecsMap.put(fieldKind, fieldSpec);
  }

  @Override
  public void addMethod(MethodSpecKind methodKind, MethodSpec methodSpec) {
    methodSpecsMap.put(methodKind, methodSpec);
  }

  @Override
  public void addType(TypeSpecKind typeKind, TypeSpec typeSpec) {
    typeSpecsMap.put(typeKind, typeSpec);
  }

  @Override
  public TypeSpec generate() {
    TypeSpec.Builder builder =
        classBuilder(getTopLevelClassName(graph.componentDescriptor())).addModifiers(FINAL);

    if (graph.componentTypeElement().isPublic()) {
      builder.addModifiers(PUBLIC);
    }

    fieldSpecsMap.asMap().values().forEach(builder::addFields);
    methodSpecsMap.asMap().values().forEach(builder::addMethods);
    typeSpecsMap.asMap().values().forEach(builder::addTypes);

    return builder.addMethod(constructorBuilder().addModifiers(PRIVATE).build()).build();
  }
}
