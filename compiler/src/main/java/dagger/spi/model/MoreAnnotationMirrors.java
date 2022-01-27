/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.spi.model;

import static io.jbock.auto.common.AnnotationMirrors.getAnnotationValuesWithDefaults;
import static java.util.stream.Collectors.joining;

import io.jbock.javapoet.CodeBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

/** Utility class for qualifier transformations */
final class MoreAnnotationMirrors {
  /**
   * Returns a String rendering of an {@link AnnotationMirror} that includes attributes in the order
   * defined in the annotation type.
   */
  public static String toStableString(DaggerAnnotation qualifier) {
    return stableAnnotationMirrorToString(qualifier.java());
  }

  /**
   * Returns a String rendering of an {@link AnnotationMirror} that includes attributes in the order
   * defined in the annotation type. This will produce the same output for {@linkplain
   * com.google.auto.common.AnnotationMirrors#equivalence() equal} {@link AnnotationMirror}s even if
   * default values are omitted or their attributes were written in different orders, e.g.
   * {@code @A(b = "b", c = "c")} and {@code @A(c = "c", b = "b", attributeWithDefaultValue =
   * "default value")}.
   */
  // TODO(ronshapiro): move this to auto-common
  private static String stableAnnotationMirrorToString(AnnotationMirror qualifier) {
    StringBuilder builder = new StringBuilder("@").append(qualifier.getAnnotationType());
    Map<ExecutableElement, AnnotationValue> elementValues =
        getAnnotationValuesWithDefaults(qualifier);
    if (!elementValues.isEmpty()) {
      Map<String, String> namedValues = new LinkedHashMap<>();
      elementValues.forEach(
          (key, value) ->
              namedValues.put(
                  key.getSimpleName().toString(), stableAnnotationValueToString(value)));
      builder.append('(');
      if (namedValues.size() == 1 && namedValues.containsKey("value")) {
        // Omit "value ="
        builder.append(namedValues.get("value"));
      } else {
        builder.append(namedValues.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", ")));
      }
      builder.append(')');
    }
    return builder.toString();
  }

  private static String stableAnnotationValueToString(AnnotationValue annotationValue) {
    return annotationValue.accept(
        new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          protected String defaultAction(Object value, Void ignore) {
            return value.toString();
          }

          @Override
          public String visitString(String value, Void ignore) {
            return CodeBlock.of("$S", value).toString();
          }

          @Override
          public String visitAnnotation(AnnotationMirror value, Void ignore) {
            return stableAnnotationMirrorToString(value);
          }

          @Override
          public String visitArray(List<? extends AnnotationValue> value, Void ignore) {
            return value.stream()
                .map(MoreAnnotationMirrors::stableAnnotationValueToString)
                .collect(joining(", ", "{", "}"));
          }
        },
        null);
  }

  private MoreAnnotationMirrors() {
  }
}