/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.internal.codegen.base;

import static dagger.internal.codegen.base.Preconditions.checkArgument;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;

import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XAnnotation;
import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.javapoet.ClassName;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.TypeElement;

/** Enumeration of the kinds of modules. */
public enum ModuleKind {
  /** {@code @Module} */
  MODULE(TypeNames.MODULE),
  ;

  /** Returns the annotations for modules of the given kinds. */
  public static Set<ClassName> annotationsFor(Set<ModuleKind> kinds) {
    return kinds.stream().map(ModuleKind::annotation).collect(toImmutableSet());
  }

  public static void checkIsModule(XTypeElement moduleElement) {
    checkArgument(forAnnotatedElement(moduleElement).isPresent());
  }

  /**
   * Returns the kind of an annotated element if it is annotated with one of the module {@linkplain
   * #annotation() annotations}.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one of the module
   *     annotations
   */
  public static Optional<ModuleKind> forAnnotatedElement(XTypeElement element) {
    return forAnnotatedElement(element.toJavac());
  }

  /**
   * Returns the kind of an annotated element if it is annotated with one of the module {@linkplain
   * #annotation() annotations}.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one of the module
   *     annotations
   */
  public static Optional<ModuleKind> forAnnotatedElement(TypeElement element) {
    Set<ModuleKind> kinds = EnumSet.noneOf(ModuleKind.class);
    for (ModuleKind kind : values()) {
      if (isAnnotationPresent(element, kind.annotation())) {
        kinds.add(kind);
      }
    }

    if (kinds.size() > 1) {
      throw new IllegalArgumentException(
          element + " cannot be annotated with more than one of " + annotationsFor(kinds));
    }
    return kinds.stream().findAny();
  }

  private final ClassName moduleAnnotation;

  ModuleKind(ClassName moduleAnnotation) {
    this.moduleAnnotation = moduleAnnotation;
  }

  /**
   * Returns the annotation mirror for this module kind on the given type.
   *
   * @throws IllegalArgumentException if the annotation is not present on the type
   */
  public XAnnotation getModuleAnnotation(XTypeElement element) {
    checkArgument(
        element.hasAnnotation(moduleAnnotation),
        "annotation %s is not present on type %s",
        moduleAnnotation,
        element);
    return element.getAnnotation(moduleAnnotation);
  }

  /** Returns the annotation that marks a module of this kind. */
  public ClassName annotation() {
    return moduleAnnotation;
  }

  /** Returns the kinds of modules that a module of this kind is allowed to include. */
  public ImmutableSet<ModuleKind> legalIncludedModuleKinds() {
    return ImmutableSet.of(MODULE);
  }
}
