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

import static dagger.internal.codegen.base.Preconditions.checkState;
import static dagger.internal.codegen.collect.Sets.difference;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import dagger.internal.codegen.binding.DaggerSuperficialValidation.ValidationException;
import dagger.internal.codegen.collect.ImmutableMap;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.collect.ImmutableSetMultimap;
import dagger.internal.codegen.collect.Maps;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XProcessingStep;
import io.jbock.javapoet.ClassName;
import java.util.Map;
import java.util.Set;

/**
 * A {@code XProcessingStep} that processes one element at a time and defers any for which {@code
 * TypeNotPresentException} is thrown.
 */
public abstract class TypeCheckingProcessingStep<E extends XElement> implements XProcessingStep {

  private final SuperficialValidator superficialValidator;

  protected TypeCheckingProcessingStep(SuperficialValidator superficialValidator) {
    this.superficialValidator = superficialValidator;
  }

  @Override
  public final ImmutableSet<String> annotations() {
    return annotationClassNames().stream().map(ClassName::canonicalName).collect(toImmutableSet());
  }

  @SuppressWarnings("unchecked") // Subclass must ensure all annotated targets are of valid type.
  @Override
  public ImmutableSet<XElement> process(
      XProcessingEnv env, Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {
    ImmutableSet.Builder<XElement> deferredElements = ImmutableSet.builder();
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
              } catch (ValidationException validationException) {
                if (validationException.fromUnexpectedThrowable()) {
                  // Rethrow since the exception was created from an unexpected throwable so
                  // deferring to another round is unlikely to help.
                  throw validationException;
                }
                // TODO(bcorso): Pass the ValidationException information to XProcessing so that we
                // can include it in the error message once we've updated XProcessing to allow it.
                // For now, we just return the element like normal.
                deferredElements.add(element);
              }
            });
    return deferredElements.build();
  }

  /**
   * Processes one element. If this method throws {@code TypeNotPresentException}, the element will
   * be deferred until the next round of processing.
   *
   * @param annotations the subset of {@code XProcessingStep#annotations()} that annotate {@code
   *     element}
   */
  protected abstract void process(E element, ImmutableSet<ClassName> annotations);

  private ImmutableMap<XElement, ImmutableSet<ClassName>> inverse(
      Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {
    ImmutableMap<String, ClassName> annotationClassNames =
        annotationClassNames().stream()
            .collect(toImmutableMap(ClassName::canonicalName, className -> className));
    checkState(
        annotationClassNames.keySet().containsAll(elementsByAnnotation.keySet()),
        "Unexpected annotations for %s: %s",
        this.getClass().getCanonicalName(),
        difference(elementsByAnnotation.keySet(), annotationClassNames.keySet()));

    ImmutableSetMultimap.Builder<XElement, ClassName> builder = ImmutableSetMultimap.builder();
    elementsByAnnotation.forEach(
        (annotationName, elementSet) ->
            elementSet.forEach(
                element -> builder.put(element, annotationClassNames.get(annotationName))));

    return ImmutableMap.copyOf(Maps.transformValues(builder.build().asMap(), ImmutableSet::copyOf));
  }

  /** Returns the set of annotations processed by this processing step. */
  protected abstract Set<ClassName> annotationClassNames();

  @Override
  public void processOver(XProcessingEnv env, Map<String, Set<XElement>> elementsByAnnotation) {
  }
}
