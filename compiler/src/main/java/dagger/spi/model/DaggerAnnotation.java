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

import static dagger.internal.codegen.xprocessing.XConverters.toJavac;

import dagger.internal.codegen.xprocessing.XAnnotation;
import io.jbock.auto.common.AnnotationMirrors;
import io.jbock.auto.common.Equivalence;
import io.jbock.javapoet.ClassName;
import java.util.Objects;
import javax.lang.model.element.AnnotationMirror;

/** Wrapper type for an annotation. */
public final class DaggerAnnotation {

  private final XAnnotation annotation;
  private final Equivalence.Wrapper<AnnotationMirror> annotationMirror;

  private DaggerAnnotation(
      XAnnotation annotation,
      Equivalence.Wrapper<AnnotationMirror> annotationMirror) {
    this.annotation = annotation;
    this.annotationMirror = annotationMirror;
  }

  public static DaggerAnnotation from(XAnnotation annotation) {
    return new DaggerAnnotation(
        annotation, AnnotationMirrors.equivalence().wrap(Objects.requireNonNull(toJavac(annotation))));
  }

  Equivalence.Wrapper<AnnotationMirror> annotationMirror() {
    return annotationMirror;
  }

  public DaggerTypeElement annotationTypeElement() {
    return DaggerTypeElement.from(annotation.getType().getTypeElement());
  }

  public ClassName className() {
    return annotationTypeElement().className();
  }

  public XAnnotation xprocessing() {
    return annotation;
  }

  public AnnotationMirror java() {
    return toJavac(annotation);
  }

  @Override
  public String toString() {
    return java().toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DaggerAnnotation that = (DaggerAnnotation) o;
    return annotationMirror.equals(that.annotationMirror);
  }

  @Override
  public int hashCode() {
    return annotationMirror.hashCode();
  }
}