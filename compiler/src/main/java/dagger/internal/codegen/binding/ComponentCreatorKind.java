/*
 * Copyright (C) 2019 The Dagger Authors.
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

import java.util.Locale;

/** Enumeration of the different kinds of component creators. */
public enum ComponentCreatorKind {
  /** {@code @Component.Builder} or one of its subcomponent/production variants. */
  BUILDER("Builder"),

  /** {@code @Component.Factory} or one of its subcomponent/production variants. */
  FACTORY("Factory"),
  ;

  private final String typeName;

  ComponentCreatorKind(String typeName) {
    this.typeName = typeName;
  }

  /** Name to use as (or as part of) a type name for a creator of this kind. */
  public String typeName() {
    return typeName;
  }

  /** Name to use for a component's static method returning a creator of this kind. */
  public String methodName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
