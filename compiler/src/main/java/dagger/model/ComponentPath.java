/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static java.util.stream.Collectors.joining;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.base.Util;
import dagger.spi.model.DaggerTypeElement;
import io.jbock.javapoet.ClassName;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** A path containing a component and all of its ancestor components. */
public final class ComponentPath {

  private final List<DaggerTypeElement> components;

  private final int hashCode;
  private final Supplier<ComponentPath> parent = Suppliers.memoize(() -> {
    Preconditions.checkState(!atRoot());
    return create(components().subList(0, components().size() - 1));
  });

  private ComponentPath(List<DaggerTypeElement> components) {
    this.components = Objects.requireNonNull(components);
    this.hashCode = components.hashCode();
  }

  /** Returns a new {@link ComponentPath} from {@code components}. */
  public static ComponentPath create(Iterable<DaggerTypeElement> components) {
    return new ComponentPath(Util.listOf(components));
  }

  /**
   * Returns the component types, starting from the {@linkplain #rootComponent() root
   * component} and ending with the {@linkplain #currentComponent() current component}.
   */
  public List<DaggerTypeElement> components() {
    return components;
  }


  /**
   * Returns the root {@link dagger.Component}-annotated type
   */
  public DaggerTypeElement rootComponent() {
    return components().get(0);
  }

  /** Returns the component at the end of the path. */
  public DaggerTypeElement currentComponent() {
    List<DaggerTypeElement> components = components();
    return components.get(components.size() - 1);
  }

  /**
   * Returns the parent of the {@linkplain #currentComponent()} current component}.
   *
   * @throws IllegalStateException if the current graph is the {@linkplain #atRoot() root component}
   */
  public DaggerTypeElement parentComponent() {
    Preconditions.checkState(!atRoot());
    List<DaggerTypeElement> components = components();
    return components.get(components.size() - 2); // components.reverse().get(1)
  }

  /**
   * Returns this path's parent path.
   *
   * @throws IllegalStateException if the current graph is the {@linkplain #atRoot() root component}
   */
  public ComponentPath parent() {
    return parent.get();
  }

  /** Returns the path from the root component to the {@code child} of the current component. */
  public ComponentPath childPath(DaggerTypeElement child) {
    return create(Util.concat(components, List.of(child)));
  }

  /**
   * Returns {@code true} if the {@linkplain #currentComponent()} current component} is the
   * {@linkplain #rootComponent()} root component}.
   */
  public boolean atRoot() {
    return components().size() == 1;
  }

  @Override
  public String toString() {
    return components().stream()
        .map(DaggerTypeElement::className)
        .map(ClassName::canonicalName)
        .collect(joining(" → "));
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ComponentPath that = (ComponentPath) o;
    return hashCode == that.hashCode && components.equals(that.components);
  }
}
