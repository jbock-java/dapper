/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.internal.codegen.javapoet;

import com.squareup.javapoet.AnnotationSpec;
import dagger.internal.codegen.base.Preconditions;
import java.util.EnumSet;
import java.util.Set;

/** Static factories to create {@link AnnotationSpec}s. */
public final class AnnotationSpecs {
  /** Values for an {@link SuppressWarnings} annotation. */
  public enum Suppression {
    RAWTYPES("rawtypes"),
    UNCHECKED("unchecked"),
    FUTURE_RETURN_VALUE_IGNORED("FutureReturnValueIgnored"),
    ;

    private final String value;

    Suppression(String value) {
      this.value = value;
    }
  }

  /** Creates an {@link AnnotationSpec} for {@link SuppressWarnings}. */
  public static AnnotationSpec suppressWarnings(Suppression first, Suppression... rest) {
    return suppressWarnings(EnumSet.of(first, rest));
  }

  /** Creates an {@link AnnotationSpec} for {@link SuppressWarnings}. */
  public static AnnotationSpec suppressWarnings(Set<Suppression> suppressions) {
    Preconditions.checkArgument(!suppressions.isEmpty());
    AnnotationSpec.Builder builder = AnnotationSpec.builder(SuppressWarnings.class);
    suppressions.forEach(suppression -> builder.addMember("value", "$S", suppression.value));
    return builder.build();
  }

  private AnnotationSpecs() {
  }
}
