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

import static dagger.internal.codegen.xprocessing.JavaPoetExt.avoidClashesWithNestedClasses;

import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.javapoet.TypeSpec;

/** Convenience methods for use with JavaPoet's {@code TypeSpec}. */
public final class TypeSpecs {

  /**
   * If {@code supertype} is a class, adds it as a superclass for {@code typeBuilder}; if it is an
   * interface, adds it as a superinterface.
   *
   * @return {@code typeBuilder}
   */
  public static TypeSpec.Builder addSupertype(
      TypeSpec.Builder typeBuilder, XTypeElement supertype) {
    if (supertype.isClass()) {
      return avoidClashesWithNestedClasses(
          typeBuilder.superclass(supertype.getClassName()), supertype);
    } else if (supertype.isInterface()) {
      return avoidClashesWithNestedClasses(
          typeBuilder.addSuperinterface(supertype.getClassName()), supertype);
    }
    throw new AssertionError(supertype + " is neither a class nor an interface.");
  }

  private TypeSpecs() {}
}
