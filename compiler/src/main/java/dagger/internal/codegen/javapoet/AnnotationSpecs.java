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

import static dagger.internal.codegen.base.Preconditions.checkArgument;

import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.collect.Lists;
import io.jbock.javapoet.AnnotationSpec;

/** Static factories to create {@code AnnotationSpec}s. */
public final class AnnotationSpecs {
  /** Values for an {@code SuppressWarnings} annotation. */
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

  /** Creates an {@code AnnotationSpec} for {@code SuppressWarnings}. */
  public static AnnotationSpec suppressWarnings(Suppression first, Suppression... rest) {
    return suppressWarnings(ImmutableSet.copyOf(Lists.asList(first, rest)));
  }

  /** Creates an {@code AnnotationSpec} for {@code SuppressWarnings}. */
  public static AnnotationSpec suppressWarnings(ImmutableSet<Suppression> suppressions) {
    checkArgument(!suppressions.isEmpty());
    AnnotationSpec.Builder builder = AnnotationSpec.builder(SuppressWarnings.class);
    suppressions.forEach(suppression -> builder.addMember("value", "$S", suppression.value));
    return builder.build();
  }

  private AnnotationSpecs() {}
}
