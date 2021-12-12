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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;

import com.google.auto.common.Equivalence;
import com.google.auto.common.MoreTypes;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.langmodel.DaggerTypes;
import java.util.List;
import java.util.Objects;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/** A class that defines proper {@code equals} and {@code hashcode} for a method signature. */
public final class MethodSignature {
  private final String name;
  private final List<? extends Equivalence.Wrapper<? extends TypeMirror>> parameterTypes;
  private final List<? extends Equivalence.Wrapper<? extends TypeMirror>> thrownTypes;

  MethodSignature(
      String name,
      List<? extends Equivalence.Wrapper<? extends TypeMirror>> parameterTypes,
      List<? extends Equivalence.Wrapper<? extends TypeMirror>> thrownTypes) {
    this.name = name;
    this.parameterTypes = parameterTypes;
    this.thrownTypes = thrownTypes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MethodSignature that = (MethodSignature) o;
    return name.equals(that.name)
        && parameterTypes.equals(that.parameterTypes)
        && thrownTypes.equals(that.thrownTypes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, parameterTypes, thrownTypes);
  }

  public static MethodSignature forComponentMethod(
      ComponentMethodDescriptor componentMethod, DeclaredType componentType, DaggerTypes types) {
    ExecutableType methodType =
        MoreTypes.asExecutable(types.asMemberOf(componentType, componentMethod.methodElement()));
    return new MethodSignature(
        componentMethod.methodElement().getSimpleName().toString(),
        wrapInEquivalence(methodType.getParameterTypes()),
        wrapInEquivalence(methodType.getThrownTypes()));
  }

  private static List<? extends Equivalence.Wrapper<? extends TypeMirror>>
  wrapInEquivalence(List<? extends TypeMirror> types) {
    return types.stream().map(MoreTypes.equivalence()::wrap).collect(toImmutableList());
  }
}
