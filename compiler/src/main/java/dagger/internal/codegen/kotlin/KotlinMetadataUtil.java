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

import dagger.internal.codegen.collect.ImmutableList;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/** Utility class for interacting with Kotlin Metadata. */
public final class KotlinMetadataUtil {

  private static final KotlinMetadataUtil INSTANCE = new KotlinMetadataUtil();

  public static KotlinMetadataUtil instance() {
    return INSTANCE;
  }

  @Inject
  KotlinMetadataUtil() {
  }

  public boolean isObjectClass(TypeElement asType) {
    return false;
  }

  public boolean isCompanionObjectClass(TypeElement enclosingType) {
    return false;
  }

  public boolean hasMetadata(Element toJavac) {
    return false;
  }

  public Map<Object, String> getAllMethodNamesBySignature(TypeElement toJavac) {
    return Map.of();
  }

  public boolean isMissingSyntheticPropertyForAnnotations(VariableElement toJavac) {
    return false;
  }

  public ImmutableList<? extends AnnotationMirror> getSyntheticPropertyAnnotations(VariableElement fieldElement, ClassName qualifier) {
    return ImmutableList.of();
  }
}
