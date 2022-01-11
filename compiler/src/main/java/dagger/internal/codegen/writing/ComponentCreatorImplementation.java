/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static java.util.Objects.requireNonNull;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.binding.ComponentRequirement;
import java.util.Map;

/** The implementation of a component creator type. */
public final class ComponentCreatorImplementation {

  private final TypeSpec spec;
  private final ClassName name;
  private final Map<ComponentRequirement, FieldSpec> fields;

  ComponentCreatorImplementation(
      TypeSpec spec,
      ClassName name,
      Map<ComponentRequirement, FieldSpec> fields) {
    this.spec = requireNonNull(spec);
    this.name = requireNonNull(name);
    this.fields = requireNonNull(fields);
  }
  /** Creates a new {@link ComponentCreatorImplementation}. */
  public static ComponentCreatorImplementation create(
      TypeSpec spec, ClassName name, Map<ComponentRequirement, FieldSpec> fields) {
    return new ComponentCreatorImplementation(spec, name, fields);
  }

  /** The type spec for the creator implementation. */
  public TypeSpec spec() {
    return spec;
  }

  /** The name of the creator implementation class. */
  public ClassName name() {
    return name;
  }

  /** All fields that are present in this implementation. */
  Map<ComponentRequirement, FieldSpec> fields() {
    return fields;
  }
}
