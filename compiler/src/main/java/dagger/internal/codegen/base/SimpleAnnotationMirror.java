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

package dagger.internal.codegen.base;

import static javax.lang.model.util.ElementFilter.methodsIn;

import io.jbock.auto.common.MoreTypes;
import java.util.Map;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

/** A representation of an annotation. */
public final class SimpleAnnotationMirror implements AnnotationMirror {
  private final TypeElement annotationType;
  private final Map<String, ? extends AnnotationValue> namedValues;
  private final Map<ExecutableElement, ? extends AnnotationValue> elementValues;

  private SimpleAnnotationMirror(
      TypeElement annotationType, Map<String, ? extends AnnotationValue> namedValues) {
    Preconditions.checkArgument(
        annotationType.getKind().equals(ElementKind.ANNOTATION_TYPE),
        "annotationType must be an annotation: %s",
        annotationType);
    Preconditions.checkArgument(
        methodsIn(annotationType.getEnclosedElements()).stream()
            .map(element -> element.getSimpleName().toString())
            .collect(Collectors.toSet())
            .equals(namedValues.keySet()),
        "namedValues must have values for exactly the members in %s: %s",
        annotationType,
        namedValues);
    this.annotationType = annotationType;
    this.namedValues = namedValues;
    this.elementValues =
        Util.toMap(methodsIn(annotationType.getEnclosedElements()),
            element -> namedValues.get(element.getSimpleName().toString()));
  }

  @Override
  public DeclaredType getAnnotationType() {
    return MoreTypes.asDeclared(annotationType.asType());
  }

  @Override
  public Map<ExecutableElement, ? extends AnnotationValue> getElementValues() {
    return elementValues;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("@").append(annotationType.getQualifiedName());
    if (!namedValues.isEmpty()) {
      builder
          .append('(')
          .append(namedValues.entrySet().stream()
              .map(e -> e.getKey() + " = " + e.getValue())
              .collect(Collectors.joining(", ")))
          .append(')');
    }
    return builder.toString();
  }

  /**
   * An object representing an annotation instance.
   *
   * @param annotationType must be an annotation type with no members
   */
  public static AnnotationMirror of(TypeElement annotationType) {
    return of(annotationType, Map.of());
  }

  /**
   * An object representing an annotation instance.
   *
   * @param annotationType must be an annotation type
   * @param namedValues a value for every annotation member, including those with defaults, indexed
   *     by simple name
   */
  private static AnnotationMirror of(
      TypeElement annotationType, Map<String, ? extends AnnotationValue> namedValues) {
    return new SimpleAnnotationMirror(annotationType, namedValues);
  }
}
