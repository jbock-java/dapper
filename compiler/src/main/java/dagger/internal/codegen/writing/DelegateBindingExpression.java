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

import static dagger.internal.codegen.base.RequestKinds.requestType;
import static dagger.internal.codegen.base.Util.getOnlyElement;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.model.BindingKind.DELEGATE;
import static java.util.Objects.requireNonNull;

import com.squareup.javapoet.ClassName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.RequestKind;
import javax.lang.model.type.TypeMirror;

/** A {@link dagger.internal.codegen.writing.BindingExpression} for {@code @Binds} methods. */
final class DelegateBindingExpression extends BindingExpression {
  private final ContributionBinding binding;
  private final RequestKind requestKind;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final DaggerTypes types;

  @AssistedInject
  DelegateBindingExpression(
      @Assisted ContributionBinding binding,
      @Assisted RequestKind requestKind,
      ComponentBindingExpressions componentBindingExpressions,
      DaggerTypes types) {
    this.binding = requireNonNull(binding);
    this.requestKind = requireNonNull(requestKind);
    this.componentBindingExpressions = componentBindingExpressions;
    this.types = types;
  }

  /**
   * Returns {@code true} if the {@code @Binds} binding's scope is stronger than the scope of the
   * binding it depends on.
   */
  static boolean isBindsScopeStrongerThanDependencyScope(
      ContributionBinding bindsBinding, BindingGraph graph) {
    Preconditions.checkArgument(bindsBinding.kind().equals(DELEGATE));
    Binding dependencyBinding =
        graph.contributionBinding(getOnlyElement(bindsBinding.dependencies()).key());
    ScopeKind bindsScope = ScopeKind.get(bindsBinding);
    ScopeKind dependencyScope = ScopeKind.get(dependencyBinding);
    return bindsScope.isStrongerScopeThan(dependencyScope);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    Expression delegateExpression =
        componentBindingExpressions.getDependencyExpression(
            bindingRequest(getOnlyElement(binding.dependencies()).key(), requestKind),
            requestingClass);

    TypeMirror contributedType = binding.contributedType();
    switch (requestKind) {
      case INSTANCE:
        return instanceRequiresCast(delegateExpression, requestingClass)
            ? delegateExpression.castTo(contributedType)
            : delegateExpression;
      default:
        return castToRawTypeIfNecessary(
            delegateExpression, requestType(requestKind, contributedType, types));
    }
  }

  private boolean instanceRequiresCast(Expression delegateExpression, ClassName requestingClass) {
    // delegateExpression.type() could be Object if expression is satisfied with a raw
    // Provider's get() method.
    return !types.isAssignable(
        delegateExpression.type(), binding.contributedType())
        && isTypeAccessibleFrom(binding.contributedType(), requestingClass.packageName());
  }

  /**
   * If {@code delegateExpression} can be assigned to {@code desiredType} safely, then {@code
   * delegateExpression} is returned unchanged. If the {@code delegateExpression} is already a raw
   * type, returns {@code delegateExpression} as well, as casting would have no effect. Otherwise,
   * returns a {@link Expression#castTo(TypeMirror) casted} version of {@code delegateExpression}
   * to the raw type of {@code desiredType}.
   */
  // TODO(ronshapiro): this probably can be generalized for usage in InjectionMethods
  private Expression castToRawTypeIfNecessary(
      Expression delegateExpression, TypeMirror desiredType) {
    if (types.isAssignable(delegateExpression.type(), desiredType)) {
      return delegateExpression;
    }
    return delegateExpression.castTo(types.erasure(desiredType));
  }

  private enum ScopeKind {
    UNSCOPED,
    SINGLE_CHECK,
    DOUBLE_CHECK,
    ;

    static ScopeKind get(Binding binding) {
      return binding
          .scope()
          .map(scope -> scope.isReusable() ? SINGLE_CHECK : DOUBLE_CHECK)
          .orElse(UNSCOPED);
    }

    boolean isStrongerScopeThan(ScopeKind other) {
      return this.ordinal() > other.ordinal();
    }
  }

  @AssistedFactory
  interface Factory {
    DelegateBindingExpression create(ContributionBinding binding, RequestKind requestKind);
  }
}
