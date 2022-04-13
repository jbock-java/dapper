/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.internal.codegen.xprocessing;

import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.xprocessing.XConverters.toXProcessing;

import io.jbock.javapoet.ClassName;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@code XProcessingEnvs} helper methods. */
public final class XProcessingEnvs {

  /** Returns the erasure of the given type. */
  public static XType erasure(XType type, XProcessingEnv processingEnv) {
    return toXProcessing(
        toJavac(processingEnv).getTypeUtils().erasure(toJavac(type)), // ALLOW_TYPES_ELEMENTS
        processingEnv);
  }

  /** Returns {@code true} if {@code type1} is a subtype of {@code type2}. */
  public static boolean isSubtype(XType type1, XType type2, XProcessingEnv processingEnv) {
    return toJavac(processingEnv)
        .getTypeUtils() // ALLOW_TYPES_ELEMENTS
        .isSubtype(toJavac(type1), toJavac(type2));
  }

  /** Returns the type this method is enclosed in. */
  public static XType wrapType(ClassName wrapper, XType type, XProcessingEnv processingEnv) {
    return processingEnv.getDeclaredType(processingEnv.requireTypeElement(wrapper), type);
  }

  private XProcessingEnvs() {}
}
