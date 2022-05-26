/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.spi.model;

import static dagger.internal.codegen.xprocessing.XConverters.toJavac;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypes;
import io.jbock.auto.common.Equivalence;
import io.jbock.auto.value.AutoValue;
import javax.lang.model.type.TypeMirror;

/** Wrapper type for a type. */
@AutoValue
public abstract class DaggerType {
  public static DaggerType from(XType type) {
    Preconditions.checkNotNull(type);
    return new AutoValue_DaggerType(XTypes.equivalence().wrap(type));
  }

  abstract Equivalence.Wrapper<XType> equivalenceWrapper();

  public XType xprocessing() {
    return equivalenceWrapper().get();
  }

  public TypeMirror java() {
    return toJavac(xprocessing());
  }

  @Override
  public final String toString() {
    return xprocessing().toString();
  }
}
