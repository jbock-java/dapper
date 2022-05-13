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

package dagger.internal.codegen.processingstep;

import static dagger.internal.codegen.base.Preconditions.checkState;
import static dagger.internal.codegen.base.Throwables.getStackTraceAsString;
import static dagger.internal.codegen.collect.Sets.difference;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static javax.tools.Diagnostic.Kind.ERROR;

import dagger.internal.codegen.base.DaggerSuperficialValidation.ValidationException;
import dagger.internal.codegen.collect.ImmutableMap;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.collect.ImmutableSetMultimap;
import dagger.internal.codegen.collect.Maps;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XMessager;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XProcessingStep;
import io.jbock.javapoet.ClassName;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@code XProcessingStep} that processes one element at a time and defers any for which {@code
 * TypeNotPresentException} is thrown.
 */
public abstract class TypeCheckingProcessingStep<E extends XElement> implements XProcessingStep {

  private final List<String> lastDeferredErrorMessages = new ArrayList<>();
  @Inject XMessager messager;
  @Inject CompilerOptions compilerOptions;
  @Inject SuperficialValidator superficialValidator;

  @Override
  public final ImmutableSet<String> annotations() {
    return annotationClassNames().stream().map(ClassName::canonicalName).collect(toImmutableSet());
  }

  @SuppressWarnings("unchecked") // Subclass must ensure all annotated targets are of valid type.
  @Override
  public ImmutableSet<XElement> process(
      XProcessingEnv env, Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {
    // We only really care about the deferred error messages from the final round of processing.
    // Thus, we can clear the values stored from the previous processing round since that clearly
    // wasn't the final round, and we replace it with any deferred error messages from this round.
    lastDeferredErrorMessages.clear();
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
                // TODO(bcorso): We should be able to remove this once we replace all calls to
                // SuperficialValidation with DaggerSuperficialValidation.
                deferredElements.add(element);
                cacheErrorMessage(typeNotPresentErrorMessage(element, e), e);
              } catch (ValidationException.UnexpectedException unexpectedException) {
                // Rethrow since the exception was created from an unexpected throwable so
                // deferring to another round is unlikely to help.
                throw unexpectedException;
              } catch (ValidationException.KnownErrorType e) {
                deferredElements.add(element);
                cacheErrorMessage(knownErrorTypeErrorMessage(element, e), e);
              } catch (ValidationException.UnknownErrorType e) {
                deferredElements.add(element);
                cacheErrorMessage(unknownErrorTypeErrorMessage(element, e), e);
              }
            });
    return deferredElements.build();
  }

  @Override
  public void processOver(
      XProcessingEnv env, Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {
    // We avoid doing any actual processing here since this is run in the same round as the last
    // call to process(). Instead, we just report the last deferred error messages, if any.
    lastDeferredErrorMessages.forEach(errorMessage -> messager.printMessage(ERROR, errorMessage));
    lastDeferredErrorMessages.clear();
  }

  private void cacheErrorMessage(String errorMessage, Exception exception) {
    lastDeferredErrorMessages.add(
        compilerOptions.includeStacktraceWithDeferredErrorMessages()
            ? String.format("%s\n\n%s", errorMessage, getStackTraceAsString(exception))
            : errorMessage);
  }

  private String typeNotPresentErrorMessage(XElement element, TypeNotPresentException exception) {
    return String.format(
        "%1$s was unable to process '%2$s' because '%3$s' could not be resolved."
            + "\n"
            + "\nIf type '%3$s' is a generated type, check above for compilation errors that may "
            + "have prevented the type from being generated. Otherwise, ensure that type '%3$s' is "
            + "on your classpath.",
        this.getClass().getSimpleName(),
        element,
        exception.typeName());
  }

  private String knownErrorTypeErrorMessage(
      XElement element, ValidationException.KnownErrorType exception) {
    return String.format(
        "%1$s was unable to process '%2$s' because '%3$s' could not be resolved."
            + "\n"
            + "\nDependency trace:"
            + "\n    => %4$s"
            + "\n"
            + "\nIf type '%3$s' is a generated type, check above for compilation errors that may "
            + "have prevented the type from being generated. Otherwise, ensure that type '%3$s' is "
            + "on your classpath.",
        this.getClass().getSimpleName(),
        element,
        exception.getErrorTypeName(),
        exception.getTrace());
  }

  private String unknownErrorTypeErrorMessage(
      XElement element, ValidationException.UnknownErrorType exception) {
    return String.format(
        "%1$s was unable to process '%2$s' because one of its dependencies could not be resolved."
            + "\n"
            + "\nDependency trace:"
            + "\n    => %3$s"
            + "\n"
            + "\nIf the dependency is a generated type, check above for compilation errors that may"
            + " have prevented the type from being generated. Otherwise, ensure that the dependency"
            + " is on your classpath.",
        this.getClass().getSimpleName(), element, exception.getTrace());
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
}
