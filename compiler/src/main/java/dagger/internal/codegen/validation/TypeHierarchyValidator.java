/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.internal.codegen.validation;

import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.xprocessing.XType;
import io.jbock.auto.common.Equivalence;
import io.jbock.auto.common.MoreTypes;
import io.jbock.auto.common.SuperficialValidation;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/** Utility methods for validating the type hierarchy of a given type. */
final class TypeHierarchyValidator {
  private TypeHierarchyValidator() {
  }

  /**
   * Validate the type hierarchy of the given type including all super classes, interfaces, and
   * type parameters.
   *
   * @throws TypeNotPresentException if an type in the hierarchy is not valid.
   */
  public static void validateTypeHierarchy(XType type, DaggerTypes types) {
    validateTypeHierarchy(type.toJavac(), types);
  }

  /**
   * Validate the type hierarchy of the given type including all super classes, interfaces, and
   * type parameters.
   *
   * @throws TypeNotPresentException if an type in the hierarchy is not valid.
   */
  public static void validateTypeHierarchy(TypeMirror type, DaggerTypes types) {
    Queue<TypeMirror> queue = new ArrayDeque<>();
    Set<Equivalence.Wrapper<TypeMirror>> queued = new HashSet<>();
    queue.add(type);
    queued.add(MoreTypes.equivalence().wrap(type));
    while (!queue.isEmpty()) {
      TypeMirror currType = queue.remove();
      if (!SuperficialValidation.validateType(currType)) {
        throw new TypeNotPresentException(currType.toString(), null);
      }
      for (TypeMirror superType : types.directSupertypes(currType)) {
        if (queued.add(MoreTypes.equivalence().wrap(superType))) {
          queue.add(superType);
        }
      }
    }
  }
}
