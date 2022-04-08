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

package dagger.internal.codegen.writing;

import dagger.internal.codegen.writing.ComponentImplementation.FieldSpecKind;
import dagger.internal.codegen.writing.ComponentImplementation.MethodSpecKind;
import dagger.internal.codegen.writing.ComponentImplementation.TypeSpecKind;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.FieldSpec;
import io.jbock.javapoet.MethodSpec;
import io.jbock.javapoet.TypeSpec;

/** Represents the implementation of a generated class. */
public interface GeneratedImplementation {
  /** Returns the name of the component. */
  ClassName name();

  /** Returns a unique class name for the generated implementation based on the given name. */
  String getUniqueClassName(String name);

  /** Returns a unique field name for the generated implementation based on the given name. */
  String getUniqueFieldName(String name);

  /** Returns a unique method name for the generated implementation based on the given name. */
  String getUniqueMethodName(String name);

  /** Claims the method name for the generated implementation. */
  void claimMethodName(String name);

  /** Adds the given field to the generated implementation. */
  void addField(FieldSpecKind fieldKind, FieldSpec fieldSpec);

  /** Adds the given method to the generated implementation. */
  void addMethod(MethodSpecKind methodKind, MethodSpec methodSpec);

  /** Adds the given type to the generated implementation. */
  void addType(TypeSpecKind typeKind, TypeSpec typeSpec);

  /** Returns the {@code TypeSpec} for this generated implementation. */
  public TypeSpec generate();
}
