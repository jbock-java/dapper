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

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import javax.lang.model.element.Element;

/** Wrapper type for an element. */
public final class DaggerElement {

  private final Element element;

  private DaggerElement(Element element) {
    this.element = element;
  }

  public static DaggerElement fromJava(Element element) {
    return new DaggerElement(requireNonNull(element));
  }

  public Element java() {
    return element;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DaggerElement that = (DaggerElement) o;
    return element.equals(that.element);
  }

  @Override
  public int hashCode() {
    return Objects.hash(element);
  }

  @Override
  public String toString() {
    return java().toString();
  }
}