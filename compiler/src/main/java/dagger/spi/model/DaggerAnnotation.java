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

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.xprocessing.XAnnotation;
import dagger.internal.codegen.xprocessing.XAnnotations;
import io.jbock.auto.common.Equivalence;
import io.jbock.auto.value.AutoValue;
import io.jbock.javapoet.ClassName;
import javax.lang.model.element.AnnotationMirror;

/** Wrapper type for an annotation. */
@AutoValue
public abstract class DaggerAnnotation {

  public static DaggerAnnotation from(XAnnotation annotation) {
    Preconditions.checkNotNull(annotation);
    return new AutoValue_DaggerAnnotation(XAnnotations.equivalence().wrap(annotation));
  }

  abstract Equivalence.Wrapper<XAnnotation> equivalenceWrapper();

  public DaggerTypeElement annotationTypeElement() {
    return DaggerTypeElement.from(xprocessing().getType().getTypeElement());
  }

  public ClassName className() {
    return annotationTypeElement().className();
  }

  public XAnnotation xprocessing() {
    return equivalenceWrapper().get();
  }

  public AnnotationMirror java() {
    return toJavac(xprocessing());
  }

  @Override
  public final String toString() {
    return XAnnotations.toString(xprocessing());
  }
}
