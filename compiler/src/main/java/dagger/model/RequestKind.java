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

package dagger.model;

import dagger.Lazy;
import dagger.spi.model.Key;

/**
 * Represents the different kinds of {@link javax.lang.model.type.TypeMirror types} that may be
 * requested as dependencies for the same key. For example, {@code String}, {@code
 * Provider<String>}, and {@code Lazy<String>} can all be requested if a key exists for {@code
 * String}; they have the {@link #INSTANCE}, {@link #PROVIDER}, and {@link #LAZY} request kinds,
 * respectively.
 */
public enum RequestKind {
  /** A default request for an instance. E.g.: {@code FooType} */
  INSTANCE("Instance"),

  /** A request for a {@code Provider}. E.g.: {@code Provider<FooType>} */
  PROVIDER("Provider"),

  /** A request for a {@link Lazy}. E.g.: {@code Lazy<FooType>} */
  LAZY("Lazy"),

  /** A request for a {@code Provider} of a {@link Lazy}. E.g.: {@code Provider<Lazy<FooType>>} */
  PROVIDER_OF_LAZY("ProviderOfLazy"),
  ;

  private final String upperCamelName;

  RequestKind(String upperCamelName) {
    this.upperCamelName = upperCamelName;
  }

  /** Returns a string that represents requests of this kind for a key. */
  public String format(Key key) {
    switch (this) {
      case INSTANCE:
        return key.toString();

      case PROVIDER_OF_LAZY:
        return String.format("Provider<Lazy<%s>>", key);

      default:
        return String.format("%s<%s>", upperCamelName, key);
    }
  }

  public String upperCamelName() {
    return upperCamelName;
  }
}
