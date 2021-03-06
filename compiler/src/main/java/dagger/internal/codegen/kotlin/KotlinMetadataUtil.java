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

import dagger.internal.codegen.collect.ImmutableMap;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.xprocessing.XAnnotation;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XFieldElement;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.Optional;

/** Utility class for interacting with Kotlin Metadata. */
public final class KotlinMetadataUtil {

  @Inject
  KotlinMetadataUtil() {}

  /**
   * Returns {@code true} if this element has the Kotlin Metadata annotation or if it is enclosed in
   * an element that does.
   */
  public boolean hasMetadata(XElement element) {
    return false;
  }

  /**
   * Returns the synthetic annotations of a Kotlin property.
   *
   * <p>Note that this method only looks for additional annotations in the synthetic property
   * method, if any, of a Kotlin property and not for annotations in its backing field.
   */
  public ImmutableSet<XAnnotation> getSyntheticPropertyAnnotations(
      XFieldElement fieldElement, ClassName annotationType) {
    return ImmutableSet.of();
  }

  /**
   * Returns {@code true} if the synthetic method for annotations is missing. This can occur when
   * the Kotlin metadata of the property reports that it contains a synthetic method for annotations
   * but such method is not found since it is synthetic and ignored by the processor.
   */
  public boolean isMissingSyntheticPropertyForAnnotations(XFieldElement fieldElement) {
    return false;
  }

  public Optional<XMethodElement> getPropertyGetter(XFieldElement fieldElement) {
    return Optional.empty();
  }

  /**
   * Returns a map mapping all method signatures within the given class element, including methods
   * that it inherits from its ancestors, to their method names.
   */
  public ImmutableMap<String, String> getAllMethodNamesBySignature(XTypeElement element) {
    return ImmutableMap.of();
  }
}
