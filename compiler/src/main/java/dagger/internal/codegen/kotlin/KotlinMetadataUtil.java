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

import static dagger.internal.codegen.xprocessing.XConverters.toJavac;

import dagger.internal.codegen.collect.ImmutableCollection;
import dagger.internal.codegen.collect.ImmutableList;
import dagger.internal.codegen.collect.ImmutableMap;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XFieldElement;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/** Utility class for interacting with Kotlin Metadata. */
public final class KotlinMetadataUtil {

  @Inject
  KotlinMetadataUtil() {
  }

  /**
   * Returns {@code true} if this element has the Kotlin Metadata annotation or if it is enclosed in
   * an element that does.
   */
  public boolean hasMetadata(XElement element) {
    return false;
  }

  /**
   * Returns {@code true} if this element has the Kotlin Metadata annotation or if it is enclosed in
   * an element that does.
   */
  public boolean hasMetadata(Element element) {
    return false;
  }

  /**
   * Returns the synthetic annotations of a Kotlin property.
   *
   * <p>Note that this method only looks for additional annotations in the synthetic property
   * method, if any, of a Kotlin property and not for annotations in its backing field.
   */
  public ImmutableCollection<? extends AnnotationMirror> getSyntheticPropertyAnnotations(
      XFieldElement fieldElement, ClassName annotationType) {
    return getSyntheticPropertyAnnotations(toJavac(fieldElement), annotationType);
  }

  /**
   * Returns the synthetic annotations of a Kotlin property.
   *
   * <p>Note that this method only looks for additional annotations in the synthetic property
   * method, if any, of a Kotlin property and not for annotations in its backing field.
   */
  public ImmutableCollection<? extends AnnotationMirror> getSyntheticPropertyAnnotations(
      VariableElement fieldElement, ClassName annotationType) {
    return ImmutableList.of();
  }

  /**
   * Returns {@code true} if the synthetic method for annotations is missing. This can occur when
   * the Kotlin metadata of the property reports that it contains a synthetic method for annotations
   * but such method is not found since it is synthetic and ignored by the processor.
   */
  public boolean isMissingSyntheticPropertyForAnnotations(XFieldElement field) {
    return isMissingSyntheticPropertyForAnnotations(toJavac(field));
  }

  /**
   * Returns {@code true} if the synthetic method for annotations is missing. This can occur when
   * the Kotlin metadata of the property reports that it contains a synthetic method for annotations
   * but such method is not found since it is synthetic and ignored by the processor.
   */
  public boolean isMissingSyntheticPropertyForAnnotations(VariableElement fieldElement) {
    return false;
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

  /** Returns {@code true} if this type element is a Kotlin object or companion object. */
  public boolean isObjectOrCompanionObjectClass(TypeElement typeElement) {
    return isObjectClass(typeElement) || isCompanionObjectClass(typeElement);
  }

  /* Returns {@code true} if this type element has a Kotlin Companion Object. */
  public boolean hasEnclosedCompanionObject(TypeElement typeElement) {
    return false;
  }

  /**
   * Returns {@code true} if the given type element was declared <code>private</code> in its Kotlin
   * source.
   */
  public boolean isVisibilityPrivate(TypeElement typeElement) {
    return false;
  }

  /**
   * Returns {@code true} if the given type element was declared {@code internal} in its Kotlin
   * source.
   */
  public boolean isVisibilityInternal(TypeElement type) {
    return false;
  }

  /**
   * Returns {@code true} if the given executable element was declared {@code internal} in its
   * Kotlin source.
   */
  public boolean isVisibilityInternal(ExecutableElement method) {
    return false;
  }

  public Optional<ExecutableElement> getPropertyGetter(VariableElement fieldElement) {
    return Optional.empty();
  }

  public boolean containsConstructorWithDefaultParam(TypeElement typeElement) {
    return false;
  }

  /**
   * Returns a map mapping all method signatures within the given class element, including methods
   * that it inherits from its ancestors, to their method names.
   */
  public ImmutableMap<String, String> getAllMethodNamesBySignature(TypeElement element) {
    return ImmutableMap.of();
  }
}
