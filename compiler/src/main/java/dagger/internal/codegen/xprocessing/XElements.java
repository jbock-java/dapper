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

package dagger.internal.codegen.xprocessing;

import static dagger.internal.codegen.base.Preconditions.checkState;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.xprocessing.XElement.isConstructor;
import static dagger.internal.codegen.xprocessing.XElement.isField;
import static dagger.internal.codegen.xprocessing.XElement.isMethod;
import static dagger.internal.codegen.xprocessing.XElement.isMethodParameter;
import static dagger.internal.codegen.xprocessing.XElement.isTypeElement;
import static dagger.internal.codegen.xprocessing.XElement.isVariableElement;

import dagger.internal.codegen.collect.ImmutableSet;
import io.jbock.javapoet.ClassName;
import java.util.Collection;
import java.util.Optional;
import javax.lang.model.element.ElementKind;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@code XElement} helper methods. */
public final class XElements {

  // TODO(bcorso): Replace usages with getJvmName() once it exists.
  /** Returns the simple name of the element. */
  public static String getSimpleName(XElement element) {
    return toJavac(element).getSimpleName().toString();
  }

  /**
   * Returns the closest enclosing element that is a {@code XTypeElement} or throws an {@code
   * IllegalStateException} if one doesn't exists.
   */
  public static XTypeElement closestEnclosingTypeElement(XElement element) {
    return optionalClosestEnclosingTypeElement(element)
        .orElseThrow(() -> new IllegalStateException("No enclosing TypeElement for: " + element));
  }

  private static Optional<XTypeElement> optionalClosestEnclosingTypeElement(XElement element) {
    if (isTypeElement(element)) {
      return Optional.of(asTypeElement(element));
    } else if (isConstructor(element)) {
      return Optional.of(asConstructor(element).getEnclosingElement());
    } else if (isMethod(element)) {
      return optionalClosestEnclosingTypeElement(asMethod(element).getEnclosingElement());
    } else if (isField(element)) {
      return optionalClosestEnclosingTypeElement(asField(element).getEnclosingElement());
    } else if (isMethodParameter(element)) {
      return optionalClosestEnclosingTypeElement(
          asMethodParameter(element).getEnclosingElement());
    }
    return Optional.empty();
  }

  public static boolean isPackage(XElement element) {
    return toJavac(element).getKind() == ElementKind.PACKAGE;
  }

  public static boolean isEnumEntry(XElement element) {
    return element instanceof XEnumEntry;
  }

  public static boolean isEnum(XElement element) {
    return toJavac(element).getKind() == ElementKind.ENUM;
  }

  public static boolean isExecutable(XElement element) {
    return isConstructor(element) || isMethod(element);
  }

  public static XExecutableElement asExecutable(XElement element) {
    checkState(isExecutable(element));
    return (XExecutableElement) element;
  }

  public static XTypeElement asTypeElement(XElement element) {
    checkState(isTypeElement(element));
    return (XTypeElement) element;
  }

  // TODO(bcorso): Rename this and the XElementKt.isMethodParameter to isExecutableParameter.
  public static XExecutableParameterElement asMethodParameter(XElement element) {
    checkState(isMethodParameter(element));
    return (XExecutableParameterElement) element;
  }

  public static XFieldElement asField(XElement element) {
    checkState(isField(element));
    return (XFieldElement) element;
  }

  public static XVariableElement asVariable(XElement element) {
    checkState(isVariableElement(element));
    return (XVariableElement) element;
  }

  public static XConstructorElement asConstructor(XElement element) {
    checkState(isConstructor(element));
    return (XConstructorElement) element;
  }

  public static XMethodElement asMethod(XElement element) {
    checkState(isMethod(element));
    return (XMethodElement) element;
  }

  public static ImmutableSet<XAnnotation> getAnnotatedAnnotations(
      XAnnotated annotated, ClassName annotationName) {
    return annotated.getAllAnnotations().stream()
        .filter(annotation -> annotation.getType().getTypeElement().hasAnnotation(annotationName))
        .collect(toImmutableSet());
  }

  /** Returns {@code true} if {@code annotated} is annotated with any of the given annotations. */
  public static boolean hasAnyAnnotation(XAnnotated annotated, ClassName... annotations) {
    return hasAnyAnnotation(annotated, ImmutableSet.copyOf(annotations));
  }

  /** Returns {@code true} if {@code annotated} is annotated with any of the given annotations. */
  public static boolean hasAnyAnnotation(XAnnotated annotated, Collection<ClassName> annotations) {
    return annotations.stream().anyMatch(annotated::hasAnnotation);
  }

  /**
   * Returns any annotation from {@code annotations} that annotates {@code annotated} or else {@code
   * Optional.empty()}.
   */
  public static Optional<XAnnotation> getAnyAnnotation(
      XAnnotated annotated, ClassName... annotations) {
    return getAnyAnnotation(annotated, ImmutableSet.copyOf(annotations));
  }

  /**
   * Returns any annotation from {@code annotations} that annotates {@code annotated} or else
   * {@code Optional.empty()}.
   */
  public static Optional<XAnnotation> getAnyAnnotation(
      XAnnotated annotated, Collection<ClassName> annotations) {
    return annotations.stream()
        .filter(annotated::hasAnnotation)
        .map(annotated::getAnnotation)
        .findFirst();
  }

  /** Returns all annotations from {@code annotations} that annotate {@code annotated}. */
  public static ImmutableSet<XAnnotation> getAllAnnotations(
      XAnnotated annotated, Collection<ClassName> annotations) {
    return annotations.stream()
        .filter(annotated::hasAnnotation)
        .map(annotated::getAnnotation)
        .collect(toImmutableSet());
  }

  private XElements() {}
}
