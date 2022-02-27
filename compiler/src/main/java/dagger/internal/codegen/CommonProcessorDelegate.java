package dagger.internal.codegen;

import static dagger.internal.codegen.xprocessing.XElement.isTypeElement;

import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.xprocessing.XAnnotation;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XExecutableElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XProcessingStep;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.tools.Diagnostic;

class CommonProcessorDelegate {

  private final Class<?> processorClass;
  private final XProcessingEnv env;
  private final List<XProcessingStep> steps;

  // Type names of deferred elements from the processor.
  private final java.util.Set<String> deferredElementNames = new LinkedHashSet<>();
  // Type element names containing deferred elements from processing steps.
  private final Map<XProcessingStep, Set<String>> elementsDeferredBySteps = new LinkedHashMap<>();

  CommonProcessorDelegate(
      Class<?> processorClass, XProcessingEnv env, List<XProcessingStep> steps) {
    this.processorClass = processorClass;
    this.env = env;
    this.steps = steps;
  }

  /**
   * Get elements annotated with step's annotations from the type element in typeElementNames.
   *
   * <p>Does not traverse type element members.
   */
  private Map<String, Set<XElement>> getStepElementsByAnnotation(
      XProcessingStep step, Set<String> typeElementNames) {
    if (typeElementNames.isEmpty()) {
      return Map.of();
    }
    Set<String> stepAnnotations = step.annotations();
    Map<String, Set<XElement>> elementsByAnnotation = new LinkedHashMap<>();
    Consumer<XElement> putStepAnnotatedElements =
        element ->
            element.getAllAnnotations().stream()
                .map(XAnnotation::getQualifiedName)
                .forEach(
                    annotationName -> {
                      if (stepAnnotations.contains(annotationName)) {
                        elementsByAnnotation.merge(
                            annotationName, Set.of(element), Util::mutableUnion);
                      }
                    });
    typeElementNames.stream()
        .map(env::findTypeElement)
        .filter(Objects::nonNull)
        .forEach(
            typeElement ->
                typeElement.getEnclosedElements().stream()
                    .filter(it -> !isTypeElement(it))
                    .forEach(
                        enclosedElement -> {
                          if (enclosedElement instanceof XExecutableElement) {
                            ((XExecutableElement) enclosedElement)
                                .getParameters()
                                .forEach(putStepAnnotatedElements);
                          }
                          putStepAnnotatedElements.accept(enclosedElement);
                        }));
    return elementsByAnnotation;
  }

  void reportMissingElements(List<String> missingElementNames) {
    missingElementNames.forEach(
        missingElementName ->
            env.getMessager()
                .printMessage(
                    Diagnostic.Kind.ERROR,
                    String.format(
                        "%s was unable to process '%s' because not all of its dependencies "
                            + "could be resolved. Check for compilation errors or a circular "
                            + "dependency with generated code.",
                        processorClass.getCanonicalName(), missingElementName)));
  }
}
