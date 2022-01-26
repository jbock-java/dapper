/* Copyright (C) 2021 The Dagger Authors.
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

import com.google.auto.common.Equivalence;
import com.google.auto.common.MoreTypes;
import java.util.Objects;
import javax.lang.model.type.TypeMirror;

/** Wrapper type for a type. */
public final class DaggerType {

  private final Equivalence.Wrapper<TypeMirror> typeMirror;

  private DaggerType(Equivalence.Wrapper<TypeMirror> typeMirror) {
    this.typeMirror = typeMirror;
  }

  public static DaggerType fromJava(TypeMirror typeMirror) {
    return new DaggerType(
        MoreTypes.equivalence().wrap(Objects.requireNonNull(typeMirror)));
  }

  public Equivalence.Wrapper<TypeMirror> typeMirror() {
    return typeMirror;
  }

  public TypeMirror java() {
    return typeMirror().get();
  }

  @Override
  public String toString() {
    return java().toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DaggerType that = (DaggerType) o;
    return typeMirror.equals(that.typeMirror);
  }

  @Override
  public int hashCode() {
    return Objects.hash(typeMirror);
  }
}