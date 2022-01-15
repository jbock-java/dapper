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

package dagger.internal.codegen.binding;

import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import jakarta.inject.Inject;
import javax.lang.model.type.TypeMirror;

/**
 * Checks the assignability of one type to another, given a {@link ContributionType} context. This
 * is used by {@code BindsMethodValidator} to validate that the
 * right-hand- side of a {@link dagger.Binds} method is valid, as well as in {@code
 * DelegateBindingExpression} when the right-hand-side in generated
 * code might be an erased type due to accessibility.
 */
public final class BindsTypeChecker {
  private final DaggerTypes types;

  // TODO(bcorso): Make this pkg-private. Used by DelegateBindingExpression.
  @Inject
  public BindsTypeChecker(DaggerTypes types) {
    this.types = types;
  }

  /**
   * Checks the assignability of {@code rightHandSide} to {@code leftHandSide} given a {@link
   * ContributionType} context.
   */
  public boolean isAssignable(
      TypeMirror rightHandSide, TypeMirror leftHandSide) {
    return types.isAssignable(rightHandSide, leftHandSide);
  }
}
