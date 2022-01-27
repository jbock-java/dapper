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

import static io.jbock.auto.common.MoreElements.asExecutable;
import static io.jbock.auto.common.MoreTypes.asDeclared;
import static io.jbock.auto.common.MoreTypes.asExecutable;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations.AssistedFactoryMetadata;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.model.BindingKind;
import io.jbock.javapoet.ParameterSpec;
import io.jbock.javapoet.TypeName;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/** Utility class for generating unique assisted parameter names for a component shard. */
final class AssistedInjectionParameters {
  /**
   * Returns the list of assisted factory parameters as {@link ParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static List<ParameterSpec> assistedFactoryParameterSpecs(
      Binding binding,
      DaggerElements elements,
      DaggerTypes types,
      ShardImplementation shardImplementation) {
    Preconditions.checkArgument(binding.kind() == BindingKind.ASSISTED_FACTORY);
    AssistedFactoryMetadata metadata =
        AssistedFactoryMetadata.create(binding.bindingElement().orElseThrow().asType(), elements, types);
    ExecutableType factoryMethodType =
        asExecutable(
            types.asMemberOf(asDeclared(binding.key().type().java()), metadata.factoryMethod()));
    return assistedParameterSpecs(
        // Use the order of the parameters from the @AssistedFactory method but use the parameter
        // names of the @AssistedInject constructor.
        metadata.assistedFactoryAssistedParameters().stream()
            .map(metadata.assistedInjectAssistedParametersMap()::get)
            .collect(Collectors.toList()),
        factoryMethodType.getParameterTypes(),
        shardImplementation);
  }

  /**
   * Returns the list of assisted parameters as {@link ParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static List<ParameterSpec> assistedParameterSpecs(
      Binding binding, DaggerTypes types, ShardImplementation shardImplementation) {
    Preconditions.checkArgument(binding.kind() == BindingKind.ASSISTED_INJECTION);
    ExecutableElement constructor = asExecutable(binding.bindingElement().orElseThrow());
    ExecutableType constructorType =
        asExecutable(types.asMemberOf(asDeclared(binding.key().type().java()), constructor));
    return assistedParameterSpecs(
        constructor.getParameters(), constructorType.getParameterTypes(), shardImplementation);
  }

  private static List<ParameterSpec> assistedParameterSpecs(
      List<? extends VariableElement> paramElements,
      List<? extends TypeMirror> paramTypes,
      ShardImplementation shardImplementation) {
    List<ParameterSpec> assistedParameterSpecs = new ArrayList<>();
    for (int i = 0; i < paramElements.size(); i++) {
      VariableElement paramElement = paramElements.get(i);
      TypeMirror paramType = paramTypes.get(i);
      if (AssistedInjectionAnnotations.isAssistedParameter(paramElement)) {
        assistedParameterSpecs.add(
            ParameterSpec.builder(
                    TypeName.get(paramType),
                    shardImplementation.getUniqueFieldNameForAssistedParam(paramElement))
                .build());
      }
    }
    return assistedParameterSpecs;
  }

  private AssistedInjectionParameters() {
  }
}