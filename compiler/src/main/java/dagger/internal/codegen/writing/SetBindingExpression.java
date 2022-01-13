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

import static dagger.internal.codegen.base.Util.getOnlyElement;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.SetBuilder;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.javapoet.Expression;
import dagger.model.DependencyRequest;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/** A binding expression for multibound sets. */
final class SetBindingExpression extends SimpleInvocationBindingExpression {
  private final ProvisionBinding binding;
  private final BindingGraph graph;
  private final ComponentBindingExpressions componentBindingExpressions;

  @AssistedInject
  SetBindingExpression(
      @Assisted ProvisionBinding binding,
      BindingGraph graph,
      ComponentBindingExpressions componentBindingExpressions) {
    super(binding);
    this.binding = binding;
    this.graph = graph;
    this.componentBindingExpressions = componentBindingExpressions;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    // TODO(ronshapiro): We should also make an ImmutableSet version of SetFactory
    // TODO(ronshapiro, gak): Use Sets.immutableEnumSet() if it's available?
    switch (binding.dependencies().size()) {
      case 0:
        return collectionsStaticFactoryInvocation(requestingClass, CodeBlock.of("of()"));
      case 1: {
        DependencyRequest dependency = getOnlyElement(binding.dependencies());
        CodeBlock contributionExpression = getContributionExpression(dependency, requestingClass);
        if (isSingleValue(dependency)) {
          return collectionsStaticFactoryInvocation(
              requestingClass, CodeBlock.of("of($L)", contributionExpression));
        }
      }
      // fall through
      default:
        CodeBlock.Builder instantiation = CodeBlock.builder();
        instantiation
            .add("$T.", SetBuilder.class)
            .add(maybeTypeParameter(requestingClass));
        instantiation.add("newSetBuilder($L)", binding.dependencies().size());
        for (DependencyRequest dependency : binding.dependencies()) {
          String builderMethod = isSingleValue(dependency) ? "add" : "addAll";
          instantiation.add(
              ".$L($L)", builderMethod, getContributionExpression(dependency, requestingClass));
        }
        instantiation.add(".build()");
        return Expression.create(
            binding.key().type(),
            instantiation.build());
    }
  }

  private CodeBlock getContributionExpression(
      DependencyRequest dependency, ClassName requestingClass) {
    return componentBindingExpressions
        .getDependencyExpression(bindingRequest(dependency), requestingClass)
        .codeBlock();
  }

  private Expression collectionsStaticFactoryInvocation(
      ClassName requestingClass, CodeBlock methodInvocation) {
    return Expression.create(
        binding.key().type(),
        CodeBlock.builder()
            .add("$T.", Set.class)
            .add(maybeTypeParameter(requestingClass))
            .add(methodInvocation)
            .build());
  }

  private CodeBlock maybeTypeParameter(ClassName requestingClass) {
    TypeMirror elementType = SetType.from(binding.key()).elementType();
    return isTypeAccessibleFrom(elementType, requestingClass.packageName())
        ? CodeBlock.of("<$T>", elementType)
        : CodeBlock.of("");
  }

  private boolean isSingleValue(DependencyRequest dependency) {
    return graph.contributionBinding(dependency.key())
        .contributionType()
        .equals(ContributionType.SET);
  }

  @AssistedFactory
  interface Factory {
    SetBindingExpression create(ProvisionBinding binding);
  }
}
