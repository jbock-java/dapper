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

import static dagger.internal.codegen.extension.DaggerStreams.stream;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.extension.DaggerStreams.valuesOf;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;

import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.javapoet.TypeNames;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.TypeElement;

/** Enumeration of the different kinds of components. */
public enum ComponentKind {
  /** {@code @Component} */
  COMPONENT(TypeNames.COMPONENT, true),

  /** {@code @Subcomponent} */
  SUBCOMPONENT(TypeNames.SUBCOMPONENT, false),

  /**
   * Kind for a descriptor that was generated from a {@link dagger.Module} instead of a component
   * type in order to validate the module's bindings.
   */
  MODULE(TypeNames.MODULE, true),
  ;

  /** Returns the annotations for components of the given kinds. */
  public static Set<ClassName> annotationsFor(Iterable<ComponentKind> kinds) {
    return stream(kinds).map(ComponentKind::annotation).collect(toImmutableSet());
  }

  /** Returns the set of component kinds the given {@code element} has annotations for. */
  public static Set<ComponentKind> getComponentKinds(TypeElement element) {
    return valuesOf(ComponentKind.class)
        .filter(kind -> isAnnotationPresent(element, kind.annotation()))
        .collect(toImmutableSet());
  }

  /**
   * Returns the kind of an annotated element if it is annotated with one of the {@linkplain
   * #annotation() annotations}.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one of the
   *     annotations
   */
  public static Optional<ComponentKind> forAnnotatedElement(TypeElement element) {
    Set<ComponentKind> kinds = getComponentKinds(element);
    if (kinds.size() > 1) {
      throw new IllegalArgumentException(
          element + " cannot be annotated with more than one of " + annotationsFor(kinds));
    }
    return kinds.stream().findAny();
  }

  private final ClassName annotation;
  private final boolean isRoot;

  ComponentKind(ClassName annotation, boolean isRoot) {
    this.annotation = annotation;
    this.isRoot = isRoot;
  }

  /** Returns the annotation that marks a component of this kind. */
  public ClassName annotation() {
    return annotation;
  }

  /** Returns the kinds of modules that can be used with a component of this kind. */
  public Set<ModuleKind> legalModuleKinds() {
    return EnumSet.of(ModuleKind.MODULE);
  }

  /** Returns the kinds of subcomponents a component of this kind can have. */
  public Set<ComponentKind> legalSubcomponentKinds() {
    return EnumSet.of(SUBCOMPONENT);
  }

  /**
   * Returns {@code true} if the descriptor is for a root component (not a subcomponent).
   */
  public boolean isRoot() {
    return isRoot;
  }
}
