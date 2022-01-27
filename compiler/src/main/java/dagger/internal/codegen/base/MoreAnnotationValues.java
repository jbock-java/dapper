/*
 * Copyright (C) 2013 The Dagger Authors.
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

import static io.jbock.auto.common.AnnotationMirrors.getAnnotationValue;

import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.lang.model.util.SimpleAnnotationValueVisitor9;

/** Utility methods for working with {@link AnnotationValue} instances. */
public final class MoreAnnotationValues {
  /**
   * Returns the list of values represented by an array annotation value.
   *
   * @throws IllegalArgumentException unless {@code annotationValue} represents an array
   */
  public static List<AnnotationValue> asAnnotationValues(AnnotationValue annotationValue) {
    return annotationValue.accept(AS_ANNOTATION_VALUES, null);
  }

  private static final AnnotationValueVisitor<List<AnnotationValue>, String>
      AS_ANNOTATION_VALUES =
      new SimpleAnnotationValueVisitor8<>() {
        @Override
        public List<AnnotationValue> visitArray(
            List<? extends AnnotationValue> vals, String elementName) {
          return List.copyOf(vals);
        }

        @Override
        protected List<AnnotationValue> defaultAction(Object o, String elementName) {
          throw new IllegalArgumentException(elementName + " is not an array: " + o);
        }
      };

  /**
   * Returns the type represented by an annotation value.
   *
   * @throws IllegalArgumentException unless {@code annotationValue} represents a single type
   */
  public static TypeMirror asType(AnnotationValue annotationValue) {
    return AS_TYPE.visit(annotationValue);
  }

  private static final AnnotationValueVisitor<TypeMirror, Void> AS_TYPE =
      new SimpleAnnotationValueVisitor9<>() {
        @Override
        public TypeMirror visitType(TypeMirror t, Void p) {
          return t;
        }

        @Override
        protected TypeMirror defaultAction(Object o, Void p) {
          throw new TypeNotPresentException(o.toString(), null);
        }
      };

  /** Returns the String value of an annotation */
  public static String getStringValue(AnnotationMirror annotation, String valueName) {
    return (String) getAnnotationValue(annotation, valueName).getValue();
  }

  private MoreAnnotationValues() {
  }
}
