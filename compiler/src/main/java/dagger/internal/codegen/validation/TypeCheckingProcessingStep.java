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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.difference;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.common.BasicAnnotationProcessor.Step;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.squareup.javapoet.ClassName;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.element.Element;

/**
 * A {@link ProcessingStep} that processes one element at a time and defers any for which {@link
 * TypeNotPresentException} is thrown.
 */
// TODO(dpb): Contribute to auto-common.
public abstract class TypeCheckingProcessingStep<E extends Element> implements Step {
  private final Function<Element, E> downcaster;

  protected TypeCheckingProcessingStep(Function<Element, E> downcaster) {
    this.downcaster = checkNotNull(downcaster);
  }

  @Override
  public final ImmutableSet<String> annotations() {
    return annotationClassNames().stream().map(ClassName::canonicalName).collect(toImmutableSet());
  }

  @Override
  public ImmutableSet<Element> process(ImmutableSetMultimap<String, Element> elementsByAnnotation) {
    ImmutableMap<String, ClassName> annotationClassNames =
        annotationClassNames().stream()
            .collect(toImmutableMap(ClassName::canonicalName, className -> className));
    checkState(
        annotationClassNames.keySet().containsAll(elementsByAnnotation.keySet()),
        "Unexpected annotations for %s: %s",
        this.getClass().getName(),
        difference(elementsByAnnotation.keySet(), annotationClassNames.keySet()));

    ImmutableSet.Builder<Element> deferredElements = ImmutableSet.builder();
    ImmutableSetMultimap.copyOf(elementsByAnnotation)
        .inverse()
        .asMap()
        .forEach(
            (element, annotations) -> {
              try {
                process(
                    downcaster.apply(element),
                    annotations.stream().map(annotationClassNames::get).collect(toImmutableSet()));
              } catch (TypeNotPresentException e) {
                deferredElements.add(element);
              }
            });
    return deferredElements.build();
  }

  /**
   * Processes one element. If this method throws {@link TypeNotPresentException}, the element will
   * be deferred until the next round of processing.
   *
   * @param annotations the subset of {@link Step#annotations()} that annotate {@code element}
   */
  protected abstract void process(E element, ImmutableSet<ClassName> annotations);

  /** Returns the set of annotations processed by this {@link Step}. */
  protected abstract Set<ClassName> annotationClassNames();
}
