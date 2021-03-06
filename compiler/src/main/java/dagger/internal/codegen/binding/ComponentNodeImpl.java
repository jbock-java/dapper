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

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import dagger.internal.codegen.collect.ImmutableSet;
import dagger.spi.model.BindingGraph.ComponentNode;
import dagger.spi.model.ComponentPath;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Scope;
import io.jbock.auto.value.AutoValue;

/** An implementation of {@code ComponentNode} that also exposes the {@code ComponentDescriptor}. */
@AutoValue
public abstract class ComponentNodeImpl implements ComponentNode {
  public static ComponentNode create(
      ComponentPath componentPath, ComponentDescriptor componentDescriptor) {
    return new AutoValue_ComponentNodeImpl(componentPath, componentDescriptor);
  }

  @Override
  public final boolean isSubcomponent() {
    return componentDescriptor().isSubcomponent();
  }

  @Override
  public boolean isRealComponent() {
    return componentDescriptor().isRealComponent();
  }

  @Override
  public final ImmutableSet<DependencyRequest> entryPoints() {
    return componentDescriptor().entryPointMethods().stream()
        .map(method -> method.dependencyRequest().get())
        .collect(toImmutableSet());
  }

  @Override
  public ImmutableSet<Scope> scopes() {
    return componentDescriptor().scopes();
  }

  public abstract ComponentDescriptor componentDescriptor();

  @Override
  public final String toString() {
    return componentPath().toString();
  }
}
