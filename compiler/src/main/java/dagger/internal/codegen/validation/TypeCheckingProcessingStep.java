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
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Util;
import io.jbock.auto.common.BasicAnnotationProcessor.Step;
import io.jbock.javapoet.ClassName;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.element.Element;

/**
 * A {@link Step} that processes one element at a time and defers any for which {@link
 * TypeNotPresentException} is thrown.
 */
// TODO(dpb): Contribute to auto-common.
public abstract class TypeCheckingProcessingStep<E extends Element> implements Step {
  private final Function<Element, E> downcaster;

  protected TypeCheckingProcessingStep(Function<Element, E> downcaster) {
    this.downcaster = requireNonNull(downcaster);
  }

  @Override
  public final Set<String> annotations() {
    return annotationClassNames().stream().map(ClassName::canonicalName).collect(toImmutableSet());
  }

  @Override
  public Set<Element> process(Map<String, Set<Element>> elementsByAnnotation) {
    Map<String, ClassName> annotationClassNames =
        annotationClassNames().stream()
            .collect(toImmutableMap(ClassName::canonicalName, className -> className));
    Preconditions.checkState(
        annotationClassNames.keySet().containsAll(elementsByAnnotation.keySet()),
        "Unexpected annotations for %s: %s",
        this.getClass().getName(),
        Util.difference(elementsByAnnotation.keySet(), annotationClassNames.keySet()));

    Set<Element> deferredElements = new LinkedHashSet<>();
    elementsByAnnotation.entrySet().stream()
        .flatMap(e -> e.getValue().stream().map(v -> new SimpleImmutableEntry<>(e.getKey(), v)))
        .collect(groupingBy(Entry::getValue, mapping(Entry::getKey, toList())))
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
    return deferredElements;
  }

  /**
   * Processes one element. If this method throws {@link TypeNotPresentException}, the element will
   * be deferred until the next round of processing.
   *
   * @param annotations the subset of {@link Step#annotations()} that annotate {@code element}
   */
  protected abstract void process(E element, Set<ClassName> annotations);

  /** Returns the set of annotations processed by this {@link Step}. */
  protected abstract Set<ClassName> annotationClassNames();
}
