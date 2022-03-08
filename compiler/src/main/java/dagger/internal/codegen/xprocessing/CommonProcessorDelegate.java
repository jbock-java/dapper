package dagger.internal.codegen.xprocessing;

import static dagger.internal.codegen.xprocessing.XElement.isTypeElement;

import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.collect.Sets;
import dagger.internal.codegen.extension.DaggerStreams;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;

class CommonProcessorDelegate {

  private final Class<?> processorClass;
  private final XProcessingEnv env;
  private final List<XProcessingStep> steps;

  // Type names of deferred elements from the processor.
  private final Set<String> deferredElementNames = new LinkedHashSet<>();
  // Type element names containing deferred elements from processing steps.
  private final Map<XProcessingStep, Set<String>> elementsDeferredBySteps = new LinkedHashMap<>();

  CommonProcessorDelegate(
      Class<?> processorClass, XProcessingEnv env, List<XProcessingStep> steps) {
    this.processorClass = processorClass;
    this.env = env;
    this.steps = steps;
  }

  void processRound(XRoundEnv roundEnv) {
    Set<String> previousRoundDeferredElementNames = new LinkedHashSet<>(deferredElementNames);
    deferredElementNames.clear();
    Map<XProcessingStep, Set<String>> currentElementsDeferredByStep = new LinkedHashMap<>();
    for (XProcessingStep step : steps) {
      // Previous round processor deferred elements, these need to be re-validated.
      Map<String, Set<XElement>> previousRoundDeferredElementsByAnnotation =
          getStepElementsByAnnotation(step, previousRoundDeferredElementNames);
      // Previous round step deferred elements, these don't need to be re-validated.
      Map<String, Set<XElement>> stepDeferredElementsByAnnotation =
          getStepElementsByAnnotation(step, elementsDeferredBySteps.getOrDefault(step, Set.of()));
      Set<XElement> deferredElements = new LinkedHashSet<>();
      Map<String, Set<XElement>> elementsByAnnotation = new LinkedHashMap<>();
      for (String annotation : step.annotations()) {
        Set<XElement> annotatedElements =
            Sets.union(
                roundEnv.getElementsAnnotatedWith(annotation),
                previousRoundDeferredElementsByAnnotation.getOrDefault(annotation, Set.of()));
        // Split between valid and invalid elements. Unlike auto-common,
        // validation is only done in the annotated element from the round
        // and not in the closest enclosing type element.
        Map<Boolean, List<XElement>> partition =
            annotatedElements.stream().collect(Collectors.partitioningBy(XElement::validate));
        deferredElements.addAll(partition.get(false));
        Set<XElement> elements =
            Sets.union(
                new LinkedHashSet<>(partition.get(true)),
                stepDeferredElementsByAnnotation.getOrDefault(annotation, Set.of()));
        if (!elements.isEmpty()) {
          elementsByAnnotation.put(annotation, elements);
        }
      }
      // Store all processor deferred elements.
      deferredElementNames.addAll(
          deferredElements.stream()
              .map(this::getClosestEnclosingTypeElementName)
              .filter(Objects::nonNull)
              .collect(Collectors.toList()));
      // Only process the step if there are annotated elements found for this step.
      if (!elementsByAnnotation.isEmpty()) {
        currentElementsDeferredByStep.put(
            step,
            step.process(env, elementsByAnnotation).stream()
                .map(this::getClosestEnclosingTypeElementName)
                .filter(Objects::nonNull)
                .collect(DaggerStreams.toImmutableSet()));
      }
    }
    // Store elements deferred by steps.
    elementsDeferredBySteps.clear();
    elementsDeferredBySteps.putAll(currentElementsDeferredByStep);
  }

  List<String> processLastRound() {
    for (XProcessingStep step : steps) {
      Map<String, Set<XElement>> stepDeferredElementsByAnnotation =
          getStepElementsByAnnotation(
              step,
              Sets.union(
                  deferredElementNames, elementsDeferredBySteps.getOrDefault(step, Set.of())));
      Map<String, Set<XElement>> elementsByAnnotation = new LinkedHashMap<>();
      for (String annotation : step.annotations()) {
        Set<XElement> annotatedElements =
            stepDeferredElementsByAnnotation.getOrDefault(annotation, Set.of());
        if (!annotatedElements.isEmpty()) {
          elementsByAnnotation.put(annotation, annotatedElements);
        }
      }
      step.processOver(env, elementsByAnnotation);
    }
    // Return element names that were deferred until the last round, an error should be reported
    // for these, failing compilation. Sadly we currently don't have the mechanism to know if
    // the missing types were generated in the last round.
    return elementsDeferredBySteps.values().stream()
        .flatMap(Set::stream)
        .collect(Collectors.toList());
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
        element -> {
          for (XAnnotation annotation : element.getAllAnnotations()) {
            String annotationName = annotation.getQualifiedName();
            if (stepAnnotations.contains(annotationName)) {
              elementsByAnnotation.merge(annotationName, Set.of(element), Util::mutableUnion);
            }
          }
        };
    for (String typeElementName : typeElementNames) {
      XTypeElement typeElement = env.findTypeElement(typeElementName);
      if (typeElement == null) {
        continue;
      }
      for (XElement enclosedElement : typeElement.getEnclosedElements()) {
        if (isTypeElement(enclosedElement)) {
          continue;
        }
        if (enclosedElement instanceof XExecutableElement) {
          ((XExecutableElement) enclosedElement).getParameters().forEach(putStepAnnotatedElements);
        }
        putStepAnnotatedElements.accept(enclosedElement);
      }
      putStepAnnotatedElements.accept(typeElement);
    }
    return elementsByAnnotation;
  }

  private String getClosestEnclosingTypeElementName(XElement element) {
    XMemberContainer result = element.getClosestMemberContainer();
    if (result == null) {
      return null;
    }
    if (!(result instanceof XTypeElement)) {
      return null;
    }
    return ((XTypeElement) result).getQualifiedName();
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
