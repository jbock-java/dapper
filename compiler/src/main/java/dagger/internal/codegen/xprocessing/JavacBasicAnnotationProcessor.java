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

import static io.jbock.auto.common.MoreElements.asExecutable;
import static io.jbock.auto.common.MoreElements.asPackage;
import static io.jbock.auto.common.SuperficialValidation.validateElement;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static javax.lang.model.element.ElementKind.PACKAGE;
import static javax.tools.Diagnostic.Kind.ERROR;

import dagger.internal.codegen.XBasicAnnotationProcessor;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.collect.ImmutableList;
import io.jbock.auto.common.MoreTypes;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor8;

/**
 * Javac implementation of a {@link XBasicAnnotationProcessor} with built-in support for validating
 * and deferring elements.
 */
public abstract class JavacBasicAnnotationProcessor extends AbstractProcessor
    implements XBasicAnnotationProcessor {

  private final Set<ElementName> deferredElementNames = new LinkedHashSet<>();
  private final Map<Step, Set<ElementName>> elementsDeferredBySteps = new LinkedHashMap<>();

  private final Function<Map<String, String>, XProcessingEnvConfig> configFunction;

  private final Supplier<JavacProcessingEnv> xEnv =
      Suppliers.memoize(() -> new JavacProcessingEnv(processingEnv));

  private final Supplier<CommonProcessorDelegate> commonDelegate =
      Suppliers.memoize(() -> new CommonProcessorDelegate(getClass(), xEnv(), steps()));

  private Elements elements;
  private Messager messager;
  private List<Step> legacySteps;

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
    this.elements = processingEnv.getElementUtils();
    this.messager = processingEnv.getMessager();
    this.legacySteps = new ArrayList<>();
    legacySteps().forEach(step -> this.legacySteps.add(step));
  }

  /**
   * Initializes the processor with the processing environment.
   *
   * <p>This will be invoked before any other function in this interface and before any steps.
   */
  @Override
  public void initialize(XProcessingEnv processingEnv) {}

  /**
   * Creates {@linkplain Step processing steps} for this processor. {@link #processingEnv} is
   * guaranteed to be set when this method is invoked.
   */
  protected abstract Iterable<? extends Step> legacySteps();

  /**
   * An optional hook for logic to be executed at the end of each round.
   *
   * @deprecated use {@link #postRound(RoundEnvironment)} instead
   */
  @Deprecated
  protected void postProcess() {}

  /** An optional hook for logic to be executed at the end of each round. */
  protected void postRound(RoundEnvironment roundEnv) {
    if (!roundEnv.processingOver()) {
      postProcess();
    }
  }

  private Set<TypeElement> getSupportedAnnotationTypeElements() {
    Preconditions.checkState(legacySteps != null);
    return legacySteps.stream()
        .flatMap(step -> getSupportedAnnotationTypeElements(step).stream())
        .collect(toUnmodifiableSet());
  }

  private Set<TypeElement> getSupportedAnnotationTypeElements(Step step) {
    return step.annotations().stream()
        .map(elements::getTypeElement)
        .filter(Objects::nonNull)
        .collect(toUnmodifiableSet());
  }

  /**
   * Returns the set of supported annotation types as collected from registered {@linkplain Step
   * processing steps}.
   */
  @Override
  public final Set<String> getSupportedAnnotationTypes() {
    Preconditions.checkState(legacySteps != null);
    return legacySteps.stream()
        .flatMap(step -> step.annotations().stream())
        .collect(toUnmodifiableSet());
  }

  @Override
  public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Preconditions.checkState(elements != null);
    Preconditions.checkState(messager != null);
    Preconditions.checkState(legacySteps != null);

    // If this is the last round, report all of the missing elements if there
    // were no errors raised in the round; otherwise reporting the missing
    // elements just adds noise the output.
    if (roundEnv.processingOver()) {
      postRound(roundEnv);
      if (!roundEnv.errorRaised()) {
        Set<ElementName> missingElements = new LinkedHashSet<>(deferredElementNames);
        elementsDeferredBySteps.values().forEach(missingElements::addAll);
        reportMissingElements(missingElements);
      }
      return false;
    }

    process(validElements(roundEnv));
    postRound(roundEnv);
    xEnv().clearCache();

    return false;
  }

  /** Processes the valid elements, including those previously deferred by each step. */
  private void process(Map<TypeElement, Set<Element>> validElements) {
    for (Step step : legacySteps) {
      Set<TypeElement> annotationTypes = getSupportedAnnotationTypeElements(step);
      Map<TypeElement, Set<Element>> stepElements = new LinkedHashMap<>();
      indexByAnnotation(elementsDeferredBySteps.getOrDefault(step, Set.of()), annotationTypes);
      stepElements.putAll(
          indexByAnnotation(elementsDeferredBySteps.getOrDefault(step, Set.of()), annotationTypes));
      validElements.forEach(
          (el, elements) -> {
            if (annotationTypes.contains(el)) {
              stepElements.put(el, elements);
            }
          });
      if (stepElements.isEmpty()) {
        elementsDeferredBySteps.remove(step);
      } else {
        Set<? extends Element> rejectedElements =
            step.process(toClassNameKeyedMultimap(stepElements));
        elementsDeferredBySteps.put(
            step,
            rejectedElements.stream()
                .map(ElementName::forAnnotatedElement)
                .collect(Collectors.toSet()));
      }
    }
  }

  private void reportMissingElements(Set<ElementName> missingElementNames) {
    for (ElementName missingElementName : missingElementNames) {
      Optional<? extends Element> missingElement = missingElementName.getElement(elements);
      if (missingElement.isPresent()) {
        messager.printMessage(
            ERROR,
            processingErrorMessage(
                "this " + missingElement.get().getKind().name().toLowerCase(Locale.ROOT)),
            missingElement.get());
      } else {
        messager.printMessage(ERROR, processingErrorMessage(missingElementName.name()));
      }
    }
  }

  private String processingErrorMessage(String target) {
    return String.format(
        "[%s:MiscError] %s was unable to process %s because not all of its dependencies could be "
            + "resolved. Check for compilation errors or a circular dependency with generated "
            + "code.",
        getClass().getSimpleName(), getClass().getCanonicalName(), target);
  }

  /**
   * Returns the valid annotated elements contained in all of the deferred elements. If none are
   * found for a deferred element, defers it again.
   */
  private Map<TypeElement, Set<Element>> validElements(RoundEnvironment roundEnv) {
    Set<ElementName> prevDeferredElementNames = Set.copyOf(deferredElementNames);
    deferredElementNames.clear();

    Map<TypeElement, Set<Element>> deferredElementsByAnnotation = new LinkedHashMap<>();
    for (ElementName deferredElementName : prevDeferredElementNames) {
      Optional<? extends Element> deferredElement = deferredElementName.getElement(elements);
      if (deferredElement.isPresent()) {
        findAnnotatedElements(
            deferredElement.get(),
            getSupportedAnnotationTypeElements(),
            deferredElementsByAnnotation);
      } else {
        deferredElementNames.add(deferredElementName);
      }
    }

    Map<TypeElement, Set<Element>> validElements = new LinkedHashMap<>();

    Set<ElementName> validElementNames = new LinkedHashSet<>();

    // Look at the elements we've found and the new elements from this round and validate them.
    for (TypeElement annotationType : getSupportedAnnotationTypeElements()) {
      Set<? extends Element> roundElements = roundEnv.getElementsAnnotatedWith(annotationType);
      Set<Element> prevRoundElements =
          deferredElementsByAnnotation.getOrDefault(annotationType, Set.of());
      for (Element element :
          Stream.concat(roundElements.stream(), prevRoundElements.stream())
              .collect(toUnmodifiableSet())) {
        ElementName elementName = ElementName.forAnnotatedElement(element);
        boolean isValidElement =
            validElementNames.contains(elementName)
                || (!deferredElementNames.contains(elementName)
                    && validateElement(
                        element.getKind().equals(PACKAGE) ? element : getEnclosingType(element)));
        if (isValidElement) {
          validElements.merge(annotationType, Set.of(element), Util::mutableUnion);
          validElementNames.add(elementName);
        } else {
          deferredElementNames.add(elementName);
        }
      }
    }

    return validElements;
  }

  private Map<TypeElement, Set<Element>> indexByAnnotation(
      Set<ElementName> annotatedElements, Set<TypeElement> annotationTypes) {
    Map<TypeElement, Set<Element>> deferredElements = new LinkedHashMap<>();
    for (ElementName elementName : annotatedElements) {
      Optional<? extends Element> element = elementName.getElement(elements);
      if (element.isPresent()) {
        findAnnotatedElements(element.get(), annotationTypes, deferredElements);
      }
    }
    return deferredElements;
  }

  /**
   * Adds {@code element} and its enclosed elements to {@code annotatedElements} if they are
   * annotated with any annotations in {@code annotationTypes}. Does not traverse to member types of
   * {@code element}, so that if {@code Outer} is passed in the example below, looking for
   * {@code @X}, then {@code Outer}, {@code Outer.foo}, and {@code Outer.foo()} will be added to the
   * multimap, but neither {@code Inner} nor its members will.
   *
   * <pre><code>
   *   {@literal @}X class Outer {
   *     {@literal @}X Object foo;
   *     {@literal @}X void foo() {}
   *     {@literal @}X static class Inner {
   *       {@literal @}X Object bar;
   *       {@literal @}X void bar() {}
   *     }
   *   }
   * </code></pre>
   */
  private static void findAnnotatedElements(
      Element element,
      Set<TypeElement> annotationTypes,
      Map<TypeElement, Set<Element>> annotatedElements) {
    for (Element enclosedElement : element.getEnclosedElements()) {
      if (!enclosedElement.getKind().isClass() && !enclosedElement.getKind().isInterface()) {
        findAnnotatedElements(enclosedElement, annotationTypes, annotatedElements);
      }
    }

    // element.getEnclosedElements() does NOT return parameter elements
    if (element instanceof ExecutableElement) {
      for (Element parameterElement : asExecutable(element).getParameters()) {
        findAnnotatedElements(parameterElement, annotationTypes, annotatedElements);
      }
    }
    for (TypeElement annotationType : annotationTypes) {
      if (isAnnotationPresent(element, annotationType)) {
        annotatedElements.merge(annotationType, Set.of(element), Util::mutableUnion);
      }
    }
  }

  private static boolean isAnnotationPresent(Element element, TypeElement annotationType) {
    return element.getAnnotationMirrors().stream()
        .anyMatch(
            mirror -> MoreTypes.asTypeElement(mirror.getAnnotationType()).equals(annotationType));
  }

  /**
   * Returns the nearest enclosing {@link TypeElement} to the current element, throwing an {@link
   * IllegalArgumentException} if the provided {@link Element} is a {@link PackageElement} or is
   * otherwise not enclosed by a type.
   */
  // TODO(user) move to MoreElements and make public.
  private static TypeElement getEnclosingType(Element element) {
    return element.accept(
        new SimpleElementVisitor8<TypeElement, Void>() {
          @Override
          protected TypeElement defaultAction(Element e, Void p) {
            return e.getEnclosingElement().accept(this, p);
          }

          @Override
          public TypeElement visitType(TypeElement e, Void p) {
            return e;
          }

          @Override
          public TypeElement visitPackage(PackageElement e, Void p) {
            throw new IllegalArgumentException();
          }
        },
        null);
  }

  private static Map<String, Set<Element>> toClassNameKeyedMultimap(
      Map<TypeElement, Set<Element>> m) {
    return m.entrySet().stream()
        .map(
            e ->
                new AbstractMap.SimpleImmutableEntry<>(
                    e.getKey().getQualifiedName().toString(), e.getValue()))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (v1, v2) -> {
                  throw new IllegalArgumentException(
                      "expecting no duplicates but found: " + v1 + ", " + v1);
                },
                LinkedHashMap::new));
  }

  /**
   * The unit of processing logic that runs under the guarantee that all elements are complete and
   * well-formed. A step may reject elements that are not ready for processing but may be at a later
   * round.
   */
  public interface Step {

    /**
     * The set of fully-qualified annotation type names processed by this step.
     *
     * <p>Warning: If the returned names are not names of annotations, they'll be ignored.
     */
    Set<String> annotations();

    /**
     * The implementation of processing logic for the step. It is guaranteed that the keys in {@code
     * elementsByAnnotation} will be a subset of the set returned by {@link #annotations()}.
     *
     * @return the elements (a subset of the values of {@code elementsByAnnotation}) that this step
     *     is unable to process, possibly until a later processing round. These elements will be
     *     passed back to this step at the next round of processing.
     */
    Set<? extends Element> process(Map<String, Set<Element>> elementsByAnnotation);
  }

  /**
   * A package or type name.
   *
   * <p>It's unfortunate that we have to track types and packages separately, but since there are
   * two different methods to look them up in {@link Elements}, we end up with a lot of parallel
   * logic. :(
   *
   * <p>Packages declared (and annotated) in {@code package-info.java} are tracked as deferred
   * packages, type elements are tracked directly, and all other elements are tracked via their
   * nearest enclosing type.
   */
  private static final class ElementName {
    private enum Kind {
      PACKAGE_NAME,
      TYPE_NAME,
    }

    private final Kind kind;
    private final String name;

    private ElementName(Kind kind, Name name) {
      this.kind = requireNonNull(kind);
      this.name = name.toString();
    }

    /**
     * An {@link ElementName} for an annotated element. If {@code element} is a package, uses the
     * fully qualified name of the package. If it's a type, uses its fully qualified name.
     * Otherwise, uses the fully-qualified name of the nearest enclosing type.
     */
    static ElementName forAnnotatedElement(Element element) {
      return element.getKind() == PACKAGE
          ? new ElementName(Kind.PACKAGE_NAME, asPackage(element).getQualifiedName())
          : new ElementName(Kind.TYPE_NAME, getEnclosingType(element).getQualifiedName());
    }

    /** The fully-qualified name of the element. */
    String name() {
      return name;
    }

    /**
     * The {@link Element} whose fully-qualified name is {@link #name()}. Absent if the relevant
     * method on {@link Elements} returns {@code null}.
     */
    Optional<? extends Element> getElement(Elements elements) {
      return Optional.ofNullable(
          kind == Kind.PACKAGE_NAME
              ? elements.getPackageElement(name)
              : elements.getTypeElement(name));
    }

    @Override
    public boolean equals(Object object) {
      if (!(object instanceof ElementName)) {
        return false;
      }

      ElementName that = (ElementName) object;
      return this.kind == that.kind && this.name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(kind, name);
    }
  }
}
