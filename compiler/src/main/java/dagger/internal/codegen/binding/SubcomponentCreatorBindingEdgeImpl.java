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

import static dagger.internal.codegen.base.Util.getOnlyElement;
import static java.util.stream.Collectors.joining;

import dagger.spi.model.BindingGraph.SubcomponentCreatorBindingEdge;
import dagger.spi.model.DaggerTypeElement;
import io.jbock.javapoet.ClassName;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** An implementation of {@link SubcomponentCreatorBindingEdge}. */
public final class SubcomponentCreatorBindingEdgeImpl implements SubcomponentCreatorBindingEdge {

  private final Set<SubcomponentDeclaration> subcomponentDeclarations;

  SubcomponentCreatorBindingEdgeImpl(
      Set<SubcomponentDeclaration> subcomponentDeclarations) {
    this.subcomponentDeclarations = subcomponentDeclarations;
  }

  @Override
  public Set<DaggerTypeElement> declaringModules() {
    return subcomponentDeclarations.stream()
        .map(SubcomponentDeclaration::contributingModule)
        .flatMap(Optional::stream)
        .map(DaggerTypeElement::from)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public String toString() {
    return "subcomponent declared by "
        + (subcomponentDeclarations.size() == 1
        ? getOnlyElement(declaringModules()).className().canonicalName()
        : declaringModules().stream()
        .map(DaggerTypeElement::className)
        .map(ClassName::canonicalName)
        .collect(joining(", ", "{", "}")));
  }
}
