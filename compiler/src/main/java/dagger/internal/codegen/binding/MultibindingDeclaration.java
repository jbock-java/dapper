/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static dagger.internal.codegen.base.Preconditions.checkArgument;

import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XMethodType;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.auto.value.AutoValue;
import io.jbock.auto.value.extension.memoized.Memoized;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.ContributionType.HasContributionType;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.Key;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jakarta.inject.Inject;

/**
 * A declaration that a multibinding with a certain key is available to be injected in a component
 * even if the component has no multibindings for that key. Identified by a map- or set-returning
 * method annotated with {@code Multibinds @Multibinds}.
 */
@AutoValue
public abstract class MultibindingDeclaration extends BindingDeclaration
    implements HasContributionType {

  /**
   * The map or set key whose availability is declared. For maps, this will be {@code Map<K,
   * Provider<V>>}. For sets, this will be {@code Set<T>}.
   */
  @Override
  public abstract Key key();

  /**
   * {@code ContributionType#SET} if the declared type is a {@code Set}, or
   * {@code ContributionType#MAP} if it is a {@code Map}.
   */
  @Override
  public abstract ContributionType contributionType();

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

  /** A factory for {@code MultibindingDeclaration}s. */
  public static final class Factory {
    private final KeyFactory keyFactory;

    @Inject
    Factory(KeyFactory keyFactory) {
      this.keyFactory = keyFactory;
    }

    /** A multibinding declaration for a {@code Multibinds @Multibinds} method. */
    MultibindingDeclaration forMultibindsMethod(
        XMethodElement moduleMethod, XTypeElement moduleElement) {
      checkArgument(moduleMethod.hasAnnotation(TypeNames.MULTIBINDS));
      return forDeclaredMethod(
          moduleMethod, moduleMethod.asMemberOf(moduleElement.getType()), moduleElement);
    }

    private MultibindingDeclaration forDeclaredMethod(
        XMethodElement method, XMethodType methodType, XTypeElement contributingType) {
      XType returnType = methodType.getReturnType();
      checkArgument(
          SetType.isSet(returnType) || MapType.isMap(returnType),
          "%s must return a set or map",
          method);
      return new AutoValue_MultibindingDeclaration(
          Optional.of(method),
          Optional.of(contributingType),
          keyFactory.forMultibindsMethod(method, methodType),
          contributionType(returnType));
    }

    private ContributionType contributionType(XType returnType) {
      if (MapType.isMap(returnType)) {
        return ContributionType.MAP;
      } else if (SetType.isSet(returnType)) {
        return ContributionType.SET;
      } else {
        throw new IllegalArgumentException("Must be Map or Set: " + returnType);
      }
    }
  }
}
