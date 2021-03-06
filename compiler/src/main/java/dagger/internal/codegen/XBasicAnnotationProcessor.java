package dagger.internal.codegen;

import dagger.internal.codegen.xprocessing.JavacBasicAnnotationProcessor;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XProcessingStep;
import dagger.internal.codegen.xprocessing.XRoundEnv;
import java.util.List;

/**
 * Common interface for basic annotation processors.
 *
 * <p>A processor should not implement this interface directly and instead should extend {@link
 * JavacBasicAnnotationProcessor}.
 *
 * <p>The XProcessing Javac and KSP implementations of this interface will automatically validate
 * and defer annotated elements for the steps. If no valid annotated element is found for a {@link
 * XProcessingStep} then its {@code XProcessingStep#process} function will not be invoked, except
 * for the last round in which {@link XProcessingStep#processOver} is invoked regardless if the
 * annotated elements are valid or not. If there were invalid annotated elements until the last
 * round, then the XProcessing implementations will report an error for each invalid element.
 *
 * <p>Be aware that even though the similarity in name, the Javac implementation of this interface
 * is not 1:1 with {@code com.google.auto.common.BasicAnnotationProcessor}. Specifically, validation
 * is done for each annotated element as opposed to the enclosing type element of the annotated
 * elements for the {@code XProcessingStep}.
 */
public interface XBasicAnnotationProcessor {

  XProcessingEnv getXProcessingEnv();

  void initialize(XProcessingEnv processingEnv);

  /** The list of processing steps to execute. */
  default Iterable<XProcessingStep> processingSteps() {
    return List.of();
  }

  /** Called at the end of a processing round after all [processingSteps] have been executed. */
  default void postRound(XProcessingEnv env, XRoundEnv round) {}
}
