package dagger.internal.codegen.xprocessing;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import dagger.internal.codegen.base.Preconditions;
import io.jbock.javapoet.ClassName;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XElement} helper methods. */
public final class XElements {

  public static XExecutableParameterElement asMethodParameter(XElement element) {
    Preconditions.checkState(element.isMethodParameter());
    return (XExecutableParameterElement) element;
  }

  public static XVariableElement asVariable(XElement element) {
    Preconditions.checkState(element.isVariableElement());
    return (XVariableElement) element;
  }

  public static XMethodElement asMethod(XElement element) {
    Preconditions.checkState(element.isMethod());
    return (XMethodElement) element;
  }

  public static Set<XAnnotation> getAnnotatedAnnotations(
      XAnnotated annotated, ClassName annotationName) {
    return annotated.getAllAnnotations().stream()
        .filter(annotation -> annotation.getType().getTypeElement().hasAnnotation(annotationName))
        .collect(toImmutableSet());
  }

  /** Returns {@code true} if {@code annotated} is annotated with any of the given annotations. */
  public static boolean hasAnyAnnotation(XAnnotated annotated, Collection<ClassName> annotations) {
    return annotations.stream().anyMatch(annotated::hasAnnotation);
  }

  /**
   * Returns any annotation from {@code annotations} that annotates {@code annotated} or else
   * {@code Optional.empty()}.
   */
  public static Optional<XAnnotation> getAnyAnnotation(
      XAnnotated annotated, Collection<ClassName> annotations) {
    return annotations.stream()
        .filter(annotated::hasAnnotation)
        .map(annotated::getAnnotation)
        .findFirst();
  }

  /** Returns all annotations from {@code annotations} that annotate {@code annotated}. */
  public static Set<XAnnotation> getAllAnnotations(
      XAnnotated annotated, Collection<ClassName> annotations) {
    return annotations.stream()
        .filter(annotated::hasAnnotation)
        .map(annotated::getAnnotation)
        .collect(toImmutableSet());
  }
  private XElements() {}
}