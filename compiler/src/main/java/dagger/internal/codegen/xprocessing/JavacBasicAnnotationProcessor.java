/*
 * Copyright 2014 Google LLC
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

import static java.util.stream.Collectors.toUnmodifiableSet;

import dagger.internal.codegen.XBasicAnnotationProcessor;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * Javac implementation of a {@link XBasicAnnotationProcessor} with built-in support for validating
 * and deferring elements.
 */
public abstract class JavacBasicAnnotationProcessor extends AbstractProcessor
    implements XBasicAnnotationProcessor {

  private final Function<Map<String, String>, XProcessingEnvConfig> configFunction;

  private final Supplier<JavacProcessingEnv> xEnv =
      Suppliers.memoize(() -> new JavacProcessingEnv(processingEnv));

  private final Supplier<CommonProcessorDelegate> commonDelegate =
      Suppliers.memoize(() -> new CommonProcessorDelegate(getClass(), xEnv(), steps()));

  protected JavacBasicAnnotationProcessor(
      Function<Map<String, String>, XProcessingEnvConfig> configFunction) {
    this.configFunction = configFunction;
  }

  JavacProcessingEnv xEnv() {
    return xEnv.get();
  }

  private final Supplier<List<XProcessingStep>> stepsCache =
      Suppliers.memoize(() -> ImmutableList.copyOf(processingSteps()));

  private List<XProcessingStep> steps() {
    return stepsCache.get();
  }

  @Override
  public JavacProcessingEnv getXProcessingEnv() {
    return xEnv.get();
  }

  @Override
  public final synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    initialize(getXProcessingEnv());
  }

  /**
   * Initializes the processor with the processing environment.
   *
   * <p>This will be invoked before any other function in this interface and before any steps.
   */
  @Override
  public void initialize(XProcessingEnv processingEnv) {}

  /**
   * Returns the set of supported annotation types as collected from registered processing steps.
   */
  @Override
  public final Set<String> getSupportedAnnotationTypes() {
    return steps().stream()
        .flatMap(step -> step.annotations().stream())
        .collect(toUnmodifiableSet());
  }

  @Override
  public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    JavacRoundEnv xRoundEnv = new JavacRoundEnv(xEnv.get(), roundEnv);
    if (roundEnv.processingOver()) {
      List<String> missingElements = commonDelegate.get().processLastRound();
      postRound(xEnv(), xRoundEnv);
      if (!roundEnv.errorRaised()) {
        // Report missing elements if no error was raised to avoid being noisy.
        commonDelegate.get().reportMissingElements(missingElements);
      }
    } else {
      commonDelegate.get().processRound(xRoundEnv);
      postRound(xEnv(), xRoundEnv);
      xEnv().clearCache();
    }
    return false;
  }
}
