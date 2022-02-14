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

package dagger.internal.codegen.binding;

import static java.util.Objects.requireNonNull;

import dagger.spi.model.BindingGraph.ComponentNode;
import dagger.spi.model.ComponentPath;
import dagger.spi.model.Scope;
import java.util.Objects;
import java.util.Set;

/** An implementation of {@link ComponentNode} that also exposes the {@link ComponentDescriptor}. */
public final class ComponentNodeImpl implements ComponentNode {

  private final ComponentPath componentPath;
  private final ComponentDescriptor componentDescriptor;

  ComponentNodeImpl(
      ComponentPath componentPath,
      ComponentDescriptor componentDescriptor) {
    this.componentPath = requireNonNull(componentPath);
    this.componentDescriptor = requireNonNull(componentDescriptor);
  }

  @Override
  public ComponentPath componentPath() {
    return componentPath;
  }

  @Override
  public boolean isSubcomponent() {
    return componentDescriptor().isSubcomponent();
  }

  @Override
  public boolean isRealComponent() {
    return componentDescriptor().isRealComponent();
  }

  @Override
  public Set<Scope> scopes() {
    return componentDescriptor().scopes();
  }

  public ComponentDescriptor componentDescriptor() {
    return componentDescriptor;
  }

  public static ComponentNode create(
      ComponentPath componentPath, ComponentDescriptor componentDescriptor) {
    return new ComponentNodeImpl(componentPath, componentDescriptor);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ComponentNodeImpl that = (ComponentNodeImpl) o;
    return componentPath.equals(that.componentPath)
        && componentDescriptor.equals(that.componentDescriptor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(componentPath, componentDescriptor);
  }

  @Override
  public String toString() {
    return componentPath().toString();
  }
}
