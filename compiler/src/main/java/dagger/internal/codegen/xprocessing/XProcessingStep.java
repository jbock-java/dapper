package dagger.internal.codegen.xprocessing;

import java.util.Map;
import java.util.Set;

/**
 * Processing step to simplify processing a set of annotations.
 */
public interface XProcessingStep {

  /**
   * The implementation of processing logic for the step. It is guaranteed that the keys in
   * {@code elementsByAnnotation} will be a subset of the set returned by {@link #annotations}.
   *
   * @return the elements (a subset of the values of {@code elementsByAnnotation}) that this step
   *     is unable to process, possibly until a later processing round. These elements will be
   *     passed back to this step at the next round of processing.
   */
  Set<XElement> process(
      XProcessingEnv env,
      Map<String, Set<XElement>> elementsByAnnotation);

  /**
   * An optional hook for logic to be executed in the last round of processing.
   *
   * Unlike {@link #process}, the elements in {@code elementsByAnnotation} are not validated and are those
   * that have been kept being deferred.
   */
  void processOver(
      XProcessingEnv env,
      Map<String, Set<XElement>> elementsByAnnotation);

  /**
   * The set of annotation qualified names processed by this step.
   */
  Set<String> annotations();
}
