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

package dagger.internal.codegen.kotlin;

import dagger.internal.codegen.extension.DaggerCollectors;

import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

/** Utility class for interacting with Kotlin Metadata. */
public final class KotlinMetadataUtil {

  private final KotlinMetadataFactory metadataFactory;

  @Inject
  KotlinMetadataUtil(KotlinMetadataFactory metadataFactory) {
    this.metadataFactory = metadataFactory;
  }

  /**
   * Returns {@code true} if this element has the Kotlin Metadata annotation or if it is enclosed in
   * an element that does.
   */
  public boolean hasMetadata(Element element) {
    return false;
  }

  /**
   * Returns {@code true} if the synthetic method for annotations is missing. This can occur when
   * the Kotlin metadata of the property reports that it contains a synthetic method for annotations
   * but such method is not found since it is synthetic and ignored by the processor.
   */
  public boolean isMissingSyntheticPropertyForAnnotations(VariableElement fieldElement) {
    return metadataFactory.create(fieldElement).isMissingSyntheticAnnotationMethod(fieldElement);
  }

  /** Returns {@code true} if this type element is a Kotlin Object. */
  public boolean isObjectClass(TypeElement typeElement) {
    return false;
  }

  /** Returns {@code true} if this type element is a Kotlin data class. */
  public boolean isDataClass(TypeElement typeElement) {
    return false;
  }

  /* Returns {@code true} if this type element is a Kotlin Companion Object. */
  public boolean isCompanionObjectClass(TypeElement typeElement) {
    return false;
  }

  /* Returns {@code true} if this type element has a Kotlin Companion Object. */
  public boolean hasEnclosedCompanionObject(TypeElement typeElement) {
    return hasMetadata(typeElement)
        && metadataFactory.create(typeElement).classMetadata().companionObjectName().isPresent();
  }

  /* Returns the Companion Object element enclosed by the given type element. */
  public TypeElement getEnclosedCompanionObject(TypeElement typeElement) {
    return metadataFactory
        .create(typeElement)
        .classMetadata()
        .companionObjectName()
        .map(
            companionObjectName ->
                ElementFilter.typesIn(typeElement.getEnclosedElements()).stream()
                    .filter(
                        innerType -> innerType.getSimpleName().contentEquals(companionObjectName))
                    .collect(DaggerCollectors.onlyElement()))
        .get();
  }

  /**
   * Returns {@code true} if the given type element was declared <code>private</code> in its Kotlin
   * source.
   */
  public boolean isVisibilityPrivate(TypeElement typeElement) {
    return false;
  }

  /**
   * Returns {@code true} if the <code>@JvmStatic</code> annotation is present in the given element.
   */
  public static boolean isJvmStaticPresent(ExecutableElement element) {
    return false;
  }
}
