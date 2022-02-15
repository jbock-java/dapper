/*
 * Copyright (C) 2019 The Dagger Authors.
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

import static dagger.internal.codegen.base.Suppliers.memoize;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnyAnnotation;
import static dagger.internal.codegen.xprocessing.XAnnotations.getClassName;
import static io.jbock.auto.common.MoreTypes.asTypeElement;

import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XAnnotation;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.javapoet.ClassName;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.lang.model.element.AnnotationMirror;

/** A {@code @Module} or {@code @ProducerModule} annotation. */
public final class ModuleAnnotation {
  private static final Set<ClassName> MODULE_ANNOTATIONS =
      Set.of(TypeNames.MODULE);

  private final XAnnotation annotation;
  private final ClassName className;

  private ModuleAnnotation(
      XAnnotation annotation,
      ClassName className) {
    this.annotation = annotation;
    this.className = className;
  }

  /** The annotation itself. */
  public XAnnotation annotation() {
    return annotation;
  }

  /** Returns the {@link ClassName} name of the annotation. */
  public ClassName className() {
    return className;
  }

  /** The simple name of the annotation. */
  public String simpleName() {
    return className().simpleName();
  }

  /**
   * The types specified in the {@code includes} attribute.
   */
  private final Supplier<List<XTypeElement>> includesCache = memoize(() ->
      annotation().getAsTypeList("includes").stream()
          .map(XType::getTypeElement)
          .collect(toImmutableList()));

  public List<XTypeElement> includes() {
    return includesCache.get();
  }

  /**
   * The types specified in the {@code subcomponents} attribute.
   */
  private final Supplier<List<XTypeElement>> subcomponentsCache = memoize(() ->
      annotation().getAsTypeList("subcomponents").stream()
          .map(XType::getTypeElement)
          .collect(toImmutableList()));

  public List<XTypeElement> subcomponents() {
    return subcomponentsCache.get();
  }

  /** Returns {@code true} if the argument is a {@code @Module}. */
  public static boolean isModuleAnnotation(XAnnotation annotation) {
    return isModuleAnnotation(annotation.toJavac());
  }

  /** Returns {@code true} if the argument is a {@code @Module}. */
  public static boolean isModuleAnnotation(AnnotationMirror annotation) {
    return MODULE_ANNOTATIONS.stream()
        .map(ClassName::canonicalName)
        .anyMatch(asTypeElement(annotation.getAnnotationType()).getQualifiedName()::contentEquals);
  }

  /**
   * Creates an object that represents a {@code @Module} or {@code @ProducerModule}.
   *
   * @throws IllegalArgumentException if {@link #isModuleAnnotation(XAnnotation)} returns {@code
   *     false}
   */
  public static ModuleAnnotation moduleAnnotation(XAnnotation annotation) {
    Preconditions.checkArgument(
        isModuleAnnotation(annotation),
        "%s is not a Module or ProducerModule annotation",
        annotation);
    return new ModuleAnnotation(annotation, getClassName(annotation));
  }

  /**
   * Returns an object representing the {@code @Module} or {@code @ProducerModule} annotation if one
   * annotates {@code typeElement}.
   */
  public static Optional<ModuleAnnotation> moduleAnnotation(XElement element) {
    return getAnyAnnotation(element, TypeNames.MODULE)
        .map(ModuleAnnotation::moduleAnnotation);
  }
}
