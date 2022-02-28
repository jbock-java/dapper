package dagger.internal.codegen.xprocessing;

import java.util.Set;
import javax.annotation.processing.RoundEnvironment;

public interface XRoundEnv {

  /** The root elements in the round. */
  Set<XElement> getRootElements();

  /**
   * Returns true if no further rounds of processing will be done.
   *
   * <p>Sources generated in this round will not be not be subject to a subsequent round of
   * annotation processing, however they will be compiled.
   */
  boolean isProcessingOver();

  Set<XElement> getElementsAnnotatedWith(String annotationQualifiedName);

  /** Creates an XRoundEnv from the given Java processing parameters. */
  static XRoundEnv create(XProcessingEnv processingEnv, RoundEnvironment roundEnvironment) {
    return new JavacRoundEnv((JavacProcessingEnv) processingEnv, roundEnvironment);
  }
}
