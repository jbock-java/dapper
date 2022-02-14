/*
 * Copyright (C) 2014 The Dagger Authors.
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

import dagger.spi.model.RequestKind;

/**
 * A mapper for associating a {@link RequestKind} to a {@link FrameworkType}, dependent on the type
 * of code to be generated.
 */
public enum FrameworkTypeMapper {
  FOR_PROVIDER() {
    @Override
    public FrameworkType getFrameworkType() {
      return FrameworkType.PROVIDER;
    }
  };

  public static FrameworkTypeMapper forBindingType() {
    return FOR_PROVIDER;
  }

  public abstract FrameworkType getFrameworkType();
}
