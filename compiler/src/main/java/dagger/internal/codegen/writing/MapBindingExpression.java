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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.binding.MapKeys.getMapKeyExpression;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.model.BindingKind.MULTIBOUND_MAP;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.MapBuilder;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.model.BindingKind;
import dagger.model.DependencyRequest;
import java.util.Collections;
import javax.lang.model.type.TypeMirror;

/** A {@link BindingExpression} for multibound maps. */
final class MapBindingExpression extends SimpleInvocationBindingExpression {

  private final ProvisionBinding binding;
  private final ImmutableMap<DependencyRequest, ContributionBinding> dependencies;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final DaggerElements elements;

  @AssistedInject
  MapBindingExpression(
      @Assisted ProvisionBinding binding,
      BindingGraph graph,
      ComponentBindingExpressions componentBindingExpressions,
      DaggerElements elements) {
    super(binding);
    this.binding = binding;
    BindingKind bindingKind = this.binding.kind();
    checkArgument(bindingKind.equals(MULTIBOUND_MAP), bindingKind);
    this.componentBindingExpressions = componentBindingExpressions;
    this.elements = elements;
    this.dependencies =
        Maps.toMap(binding.dependencies(), dep -> graph.contributionBinding(dep.key()));
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    switch (dependencies.size()) {
      case 0:
        return collectionsStaticFactoryInvocation(requestingClass, CodeBlock.of("emptyMap()"));
      case 1:
        return collectionsStaticFactoryInvocation(
            requestingClass,
            CodeBlock.of(
                "singletonMap($L)",
                keyAndValueExpression(getOnlyElement(dependencies.keySet()), requestingClass)));
      default:
        CodeBlock.Builder instantiation = CodeBlock.builder();
        instantiation
            .add("$T.", MapBuilder.class)
            .add(maybeTypeParameters(requestingClass));
        instantiation.add("newMapBuilder($L)", dependencies.size());
        for (DependencyRequest dependency : dependencies.keySet()) {
          instantiation.add(".put($L)", keyAndValueExpression(dependency, requestingClass));
        }
        return Expression.create(
            binding.key().type(),
            instantiation.add(".build()").build());
    }
  }

  private CodeBlock keyAndValueExpression(DependencyRequest dependency, ClassName requestingClass) {
    return CodeBlock.of(
        "$L, $L",
        getMapKeyExpression(dependencies.get(dependency), requestingClass, elements),
        componentBindingExpressions
            .getDependencyExpression(bindingRequest(dependency), requestingClass)
            .codeBlock());
  }

  private Expression collectionsStaticFactoryInvocation(
      ClassName requestingClass, CodeBlock methodInvocation) {
    return Expression.create(
        binding.key().type(),
        CodeBlock.builder()
            .add("$T.", Collections.class)
            .add(maybeTypeParameters(requestingClass))
            .add(methodInvocation)
            .build());
  }

  private CodeBlock maybeTypeParameters(ClassName requestingClass) {
    TypeMirror bindingKeyType = binding.key().type();
    MapType mapType = MapType.from(binding.key());
    return isTypeAccessibleFrom(bindingKeyType, requestingClass.packageName())
        ? CodeBlock.of("<$T, $T>", mapType.keyType(), mapType.valueType())
        : CodeBlock.of("");
  }

  @AssistedFactory
  interface Factory {
    MapBindingExpression create(ProvisionBinding binding);
  }
}
