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

import static com.google.auto.common.MoreTypes.isType;

import com.google.auto.common.MoreTypes;
import dagger.Lazy;
import dagger.MembersInjector;
import jakarta.inject.Provider;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * A collection of utility methods for dealing with Dagger framework types. A framework type is any
 * type that the framework itself defines.
 */
public final class FrameworkTypes {
  private static final Set<Class<?>> PROVISION_TYPES =
      new LinkedHashSet<>(List.of(Provider.class, Lazy.class, MembersInjector.class));

  /** Returns true if the type represents a framework type. */
  public static boolean isFrameworkType(TypeMirror type) {
    return isType(type)
        && typeIsOneOf(PROVISION_TYPES, type);
  }

  private static boolean typeIsOneOf(Set<Class<?>> classes, TypeMirror type) {
    for (Class<?> clazz : classes) {
      if (MoreTypes.isTypeOf(clazz, type)) {
        return true;
      }
    }
    return false;
  }

  private FrameworkTypes() {
  }
}
