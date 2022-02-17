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
import static java.util.Objects.requireNonNull;

import dagger.internal.codegen.xprocessing.XExecutableElement;
import javax.lang.model.element.ExecutableElement;

/** Wrapper type for an executable element. */
public final class DaggerExecutableElement {

  private final XExecutableElement executableElement;

  private DaggerExecutableElement(XExecutableElement executableElement) {
    this.executableElement = executableElement;
  }

  public static DaggerExecutableElement from(XExecutableElement executableElement) {
    return new DaggerExecutableElement(requireNonNull(executableElement));
  }

  public XExecutableElement xprocessing() {
    return executableElement;
  }

  public ExecutableElement java() {
    return toJavac(xprocessing());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DaggerExecutableElement that = (DaggerExecutableElement) o;
    return executableElement.equals(that.executableElement);
  }

  @Override
  public int hashCode() {
    return executableElement.hashCode();
  }

  @Override
  public String toString() {
    return xprocessing().toString();
  }
}