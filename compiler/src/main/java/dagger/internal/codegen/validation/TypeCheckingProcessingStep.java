/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.internal.codegen.validation;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XProcessingStep;
import io.jbock.javapoet.ClassName;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@link XProcessingStep} that processes one element at a time and defers any for which {@link
 * TypeNotPresentException} is thrown.
 */
public abstract class TypeCheckingProcessingStep<E extends XElement> extends XProcessingStep {

  private final SuperficialValidator superficialValidator;

  protected TypeCheckingProcessingStep(SuperficialValidator superficialValidator) {
    this.superficialValidator = superficialValidator;
  }

  @Override
  public final Set<String> annotations() {
    return annotationClassNames().stream().map(ClassName::canonicalName).collect(toImmutableSet());
  }

  @SuppressWarnings("unchecked") // Subclass must ensure all annotated targets are of valid type.
  @Override
  public Set<XElement> process(
      XProcessingEnv env, Map<String, Set<XElement>> elementsByAnnotation) {
    Set<XElement> deferredElements = new LinkedHashSet<>();
    inverse(elementsByAnnotation)
        .forEach(
            (element, annotations) -> {
              try {
                // The XBasicAnnotationProcessor only validates the element itself. However, we
                // validate the enclosing type here to keep the previous behavior of
                // BasicAnnotationProcessor, since Dagger still relies on this behavior.
                // TODO(b/201479062): It's inefficient to require validation of the entire enclosing
                //  type, we should try to remove this and handle any additional validation into the
                //  steps that need it.
                superficialValidator.throwIfNearestEnclosingTypeNotValid(element);
                process((E) element, annotations);
              } catch (TypeNotPresentException e) {
                deferredElements.add(element);
              }
            });
    return deferredElements;
  }

  /**
   * Processes one element. If this method throws {@link TypeNotPresentException}, the element will
   * be deferred until the next round of processing.
   *
   * @param annotations the subset of {@link XProcessingStep#annotations()} that annotate {@code
   *     element}
   */
  protected abstract void process(E element, Set<ClassName> annotations);

  private Map<XElement, Set<ClassName>> inverse(
      Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {
    Map<String, ClassName> annotationClassNames =
        annotationClassNames().stream()
            .collect(toImmutableMap(ClassName::canonicalName, className -> className));
    Preconditions.checkState(
        annotationClassNames.keySet().containsAll(elementsByAnnotation.keySet()),
        "Unexpected annotations for %s: %s",
        this.getClass().getCanonicalName(),
        Util.difference(elementsByAnnotation.keySet(), annotationClassNames.keySet()));

    Map<XElement, Set<ClassName>> builder = new LinkedHashMap<>();
    elementsByAnnotation.forEach(
        (annotationName, elementSet) ->
            elementSet.forEach(
                element -> builder.merge(element, Set.of(annotationClassNames.get(annotationName)), Util::mutableUnion)));
    return builder;
  }

  /** Returns the set of annotations processed by this processing step. */
  protected abstract Set<ClassName> annotationClassNames();
}
