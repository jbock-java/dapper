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

package dagger.internal.codegen.base;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.auto.common.Equivalence;
import com.google.auto.common.MoreTypes;
import dagger.model.Key;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Information about a {@link Set} {@link TypeMirror}.
 */
public final class SetType {

  private final Equivalence.Wrapper<DeclaredType> wrappedDeclaredSetType;

  private SetType(Equivalence.Wrapper<DeclaredType> wrappedDeclaredSetType) {
    this.wrappedDeclaredSetType = requireNonNull(wrappedDeclaredSetType);
  }

  /**
   * The set type itself, wrapped using {@link MoreTypes#equivalence()}. Use
   * {@link #declaredSetType()} instead.
   */
  Equivalence.Wrapper<DeclaredType> wrappedDeclaredSetType() {
    return wrappedDeclaredSetType;
  }

  /**
   * The set type itself.
   */
  private DeclaredType declaredSetType() {
    return wrappedDeclaredSetType().get();
  }

  /**
   * {@code true} if the set type is the raw {@link Set} type.
   */
  public boolean isRawType() {
    return declaredSetType().getTypeArguments().isEmpty();
  }

  /**
   * The element type.
   */
  public TypeMirror elementType() {
    return declaredSetType().getTypeArguments().get(0);
  }

  /**
   * {@code true} if {@link #elementType()} is a {@code clazz}.
   */
  public boolean elementsAreTypeOf(Class<?> clazz) {
    return MoreTypes.isType(elementType()) && MoreTypes.isTypeOf(clazz, elementType());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SetType setType = (SetType) o;
    return wrappedDeclaredSetType.equals(setType.wrappedDeclaredSetType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(wrappedDeclaredSetType);
  }

  /**
   * {@code true} if {@code type} is a {@link Set} type.
   */
  public static boolean isSet(TypeMirror type) {
    return MoreTypes.isType(type) && MoreTypes.isTypeOf(Set.class, type);
  }

  /**
   * {@code true} if {@code key.type()} is a {@link Set} type.
   */
  public static boolean isSet(Key key) {
    return isSet(key.type());
  }

  /**
   * Returns a {@link SetType} for {@code type}.
   *
   * @throws IllegalArgumentException if {@code type} is not a {@link Set} type
   */
  public static SetType from(TypeMirror type) {
    checkArgument(isSet(type), "%s must be a Set", type);
    return new SetType(MoreTypes.equivalence().wrap(MoreTypes.asDeclared(type)));
  }

  /**
   * Returns a {@link SetType} for {@code key}'s {@link Key#type() type}.
   *
   * @throws IllegalArgumentException if {@code key.type()} is not a {@link Set} type
   */
  public static SetType from(Key key) {
    return from(key.type());
  }
}
