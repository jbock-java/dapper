package dagger.internal.codegen;

import static dagger.internal.codegen.xprocessing.XElement.isConstructor;
import static dagger.internal.codegen.xprocessing.XElement.isField;
import static dagger.internal.codegen.xprocessing.XElement.isMethod;
import static dagger.internal.codegen.xprocessing.XElement.isMethodParameter;
import static dagger.internal.codegen.xprocessing.XElement.isTypeElement;

import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.collect.ImmutableMap;
import dagger.internal.codegen.extension.DaggerStreams;
import dagger.internal.codegen.xprocessing.XAnnotation;
import dagger.internal.codegen.xprocessing.XConstructorElement;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XExecutableElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XProcessingStep;
import dagger.internal.codegen.xprocessing.XRoundEnv;
import dagger.internal.codegen.xprocessing.XTypeElement;
import java.util.AbstractMap.SimpleImmutableEntry;
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
    ImmutableMap<XProcessingStep, Set<String>> currentElementsDeferredByStep =
        steps.stream()
            .map(
                step -> {
                  // Previous round processor deferred elements, these need to be re-validated.
                  Map<String, Set<XElement>> previousRoundDeferredElementsByAnnotation =
                      getStepElementsByAnnotation(step, previousRoundDeferredElementNames);
                  // Previous round step deferred elements, these don't need to be re-validated.
                  Map<String, Set<XElement>> stepDeferredElementsByAnnotation =
                      getStepElementsByAnnotation(
                          step, elementsDeferredBySteps.getOrDefault(step, Set.of()));
                  Set<XElement> deferredElements = new LinkedHashSet<>();
                  ImmutableMap<String, Set<XElement>> elementsByAnnotation =
                      step.annotations().stream()
                          .map(
                              annotation -> {
                                Set<XElement> annotatedElements =
                                    Util.union(
                                        roundEnv.getElementsAnnotatedWith(annotation),
                                        previousRoundDeferredElementsByAnnotation.getOrDefault(
                                            annotation, Set.of()));
                                // Split between valid and invalid elements. Unlike auto-common,
                                // validation is only
                                // done in the annotated element from the round and not in the
                                // closest
                                // enclosing
                                // type element.
                                Map<Boolean, List<XElement>> foobar =
                                    annotatedElements.stream()
                                        .collect(Collectors.partitioningBy(XElement::validate));
                                List<XElement> validElements = foobar.get(true);
                                List<XElement> invalidElements = foobar.get(false);
                                deferredElements.addAll(invalidElements);
                                Set<XElement> it =
                                    Util.union(
                                        new LinkedHashSet<>(validElements),
                                        stepDeferredElementsByAnnotation.getOrDefault(
                                            annotation, Set.of()));
                                if (!it.isEmpty()) {
                                  return new SimpleImmutableEntry<>(annotation, it);
                                } else {
                                  return null;
                                }
                              })
                          .filter(Objects::nonNull)
                          .collect(
                              DaggerStreams.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
                  // Store all processor deferred elements.
                  deferredElementNames.addAll(
                      deferredElements.stream()
                          .map(
                              it -> {
                                XTypeElement el = getClosestEnclosingTypeElement(it);
                                if (el == null) {
                                  return null;
                                }
                                return el.getQualifiedName();
                              })
                          .filter(Objects::nonNull)
                          .collect(Collectors.toList()));
                  // Only process the step if there are annotated elements found for this step.
                  if (!elementsByAnnotation.isEmpty()) {
                    return new SimpleImmutableEntry<>(
                        step,
                        step.process(env, elementsByAnnotation).stream()
                            .map(
                                it -> {
                                  XTypeElement el = getClosestEnclosingTypeElement(it);
                                  if (el == null) {
                                    return null;
                                  }
                                  return el.getQualifiedName();
                                })
                            .filter(Objects::nonNull)
                            .collect(DaggerStreams.toImmutableSet()));
                  } else {
                    return new SimpleImmutableEntry<>(step, Set.<String>of());
                  }
                })
            .collect(DaggerStreams.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    // Store elements deferred by steps.
    elementsDeferredBySteps.clear();
    elementsDeferredBySteps.putAll(currentElementsDeferredByStep);
  }

  List<String> processLastRound() {
    steps.stream()
        .forEach(
            step -> {
              Map<String, Set<XElement>> stepDeferredElementsByAnnotation =
                  getStepElementsByAnnotation(
                      step,
                      Util.union(
                          deferredElementNames,
                          elementsDeferredBySteps.getOrDefault(step, Set.of())));
              Map<String, Set<XElement>> elementsByAnnotation =
                  step.annotations().stream()
                      .map(
                          annotation -> {
                            Set<XElement> annotatedElements =
                                stepDeferredElementsByAnnotation.getOrDefault(annotation, Set.of());
                            if (!annotatedElements.isEmpty()) {
                              return new SimpleImmutableEntry<>(annotation, annotatedElements);
                            } else {
                              return null;
                            }
                          })
                      .filter(Objects::nonNull)
                      .collect(
                          DaggerStreams.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
              step.processOver(env, elementsByAnnotation);
            });
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

  // TODO(b/201308409): Does not work with top-level KSP functions or properties whose
  //  container are synthetic.
  private XTypeElement getClosestEnclosingTypeElement(XElement element) {
    if (isTypeElement(element)) {
      return (XTypeElement) element;
    }
    if (isField(element)) {
      return (XTypeElement) element.getEnclosingElement();
    }
    if (isMethod(element)) {
      return (XTypeElement) element.getEnclosingElement();
    }
    if (isConstructor(element)) {
      return ((XConstructorElement) element).getEnclosingElement();
    }
    if (isMethodParameter(element)) {
      return (XTypeElement) element.getEnclosingElement();
    }
    // TODO enum entry
    env.getMessager()
        .printMessage(
            Diagnostic.Kind.WARNING,
            String.format(
                "Unable to defer element '%s': Don't know how to find "
                    + "closest enclosing type element.",
                element));
    return null;
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
