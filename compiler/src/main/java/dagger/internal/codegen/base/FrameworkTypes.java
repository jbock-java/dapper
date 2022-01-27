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

import static io.jbock.auto.common.MoreTypes.isType;

import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerTypes;
import io.jbock.javapoet.ClassName;
import java.util.List;
import javax.lang.model.type.TypeMirror;

/**
 * A collection of utility methods for dealing with Dagger framework types. A framework type is any
 * type that the framework itself defines.
 */
public final class FrameworkTypes {
  private static final List<ClassName> PROVISION_TYPES =
      List.of(TypeNames.PROVIDER, TypeNames.LAZY);

  /** Returns true if the type represents a framework type. */
  public static boolean isFrameworkType(TypeMirror type) {
    if (!isType(type)) {
      return false;
    }
    for (ClassName clazz : FrameworkTypes.PROVISION_TYPES) {
      if (DaggerTypes.isTypeOf(clazz, type)) {
        return true;
      }
    }
    return false;
  }

  private FrameworkTypes() {
  }
}
