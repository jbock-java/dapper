/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.auto.common.AnnotationMirrors.getAnnotationElementAndValue;
import static dagger.internal.codegen.binding.ConfigurationAnnotations.getSubcomponentCreator;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.ModuleAnnotation;
import dagger.internal.codegen.base.Suppliers;
import dagger.model.Key;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * A declaration for a subcomponent that is included in a module via {@link
 * dagger.Module#subcomponents()}.
 */
public final class SubcomponentDeclaration extends BindingDeclaration {
  private final Optional<Element> bindingElement;
  private final Optional<TypeElement> contributingModule;
  private final Key key;
  private final TypeElement subcomponentType;
  private final ModuleAnnotation moduleAnnotation;
  private final IntSupplier hash = Suppliers.memoizeInt(() ->
      Objects.hash(bindingElement(), contributingModule(), key(), subcomponentType(), moduleAnnotation()));

  SubcomponentDeclaration(
      Optional<Element> bindingElement,
      Optional<TypeElement> contributingModule,
      Key key,
      TypeElement subcomponentType,
      ModuleAnnotation moduleAnnotation) {
    this.bindingElement = requireNonNull(bindingElement);
    this.contributingModule = requireNonNull(contributingModule);
    this.key = requireNonNull(key);
    this.subcomponentType = requireNonNull(subcomponentType);
    this.moduleAnnotation = requireNonNull(moduleAnnotation);
  }

  @Override
  public Optional<Element> bindingElement() {
    return bindingElement;
  }

  @Override
  public Optional<TypeElement> contributingModule() {
    return contributingModule;
  }

  /**
   * Key for the {@link dagger.Subcomponent.Builder} or {@link
   * dagger.producers.ProductionSubcomponent.Builder} of {@link #subcomponentType()}.
   */
  @Override
  public Key key() {
    return key;
  }

  /**
   * The type element that defines the {@link dagger.Subcomponent} or {@link
   * dagger.producers.ProductionSubcomponent} for this declaration.
   */
  TypeElement subcomponentType() {
    return subcomponentType;
  }

  /** The module annotation. */
  public ModuleAnnotation moduleAnnotation() {
    return moduleAnnotation;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SubcomponentDeclaration that = (SubcomponentDeclaration) o;
    return hashCode() == that.hashCode()
        && bindingElement.equals(that.bindingElement)
        && contributingModule.equals(that.contributingModule)
        && key.equals(that.key)
        && subcomponentType.equals(that.subcomponentType)
        && moduleAnnotation.equals(that.moduleAnnotation);
  }

  @Override
  public int hashCode() {
    return hash.getAsInt();
  }

  /** A {@link SubcomponentDeclaration} factory. */
  public static class Factory {
    private final KeyFactory keyFactory;

    @Inject
    Factory(KeyFactory keyFactory) {
      this.keyFactory = keyFactory;
    }

    ImmutableSet<SubcomponentDeclaration> forModule(TypeElement module) {
      ImmutableSet.Builder<SubcomponentDeclaration> declarations = ImmutableSet.builder();
      ModuleAnnotation moduleAnnotation = ModuleAnnotation.moduleAnnotation(module).get();
      Element subcomponentAttribute =
          getAnnotationElementAndValue(moduleAnnotation.annotation(), "subcomponents").getKey();
      for (TypeElement subcomponent : moduleAnnotation.subcomponents()) {
        declarations.add(
            new SubcomponentDeclaration(
                Optional.of(subcomponentAttribute),
                Optional.of(module),
                keyFactory.forSubcomponentCreator(
                    getSubcomponentCreator(subcomponent).get().asType()),
                subcomponent,
                moduleAnnotation));
      }
      return declarations.build();
    }
  }
}
