package dagger.internal.codegen.xprocessing;

import dagger.internal.codegen.langmodel.DaggerElements;
import io.jbock.javapoet.ClassName;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

public abstract class XElement implements XAnnotated, XHasModifiers {

  private final XProcessingEnv env;
  private final Element element;

  public XElement(Element element, XProcessingEnv env) {
    this.env = env;
    this.element = element;
  }

  public Element toJavac() {
    return element;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    XElement xElement = (XElement) o;
    return element.equals(xElement.element);
  }

  @Override
  public int hashCode() {
    return Objects.hash(element);
  }

  @Override
  public final boolean isPublic() {
    return element.getModifiers().contains(Modifier.PUBLIC);
  }

  @Override
  public final boolean isPrivate() {
    return element.getModifiers().contains(Modifier.PRIVATE);
  }

  @Override
  public final boolean isAbstract() {
    return element.getModifiers().contains(Modifier.ABSTRACT);
  }

  public final boolean hasAnyOf(Iterable<ClassName> classNames) {
    return DaggerElements.isAnyAnnotationPresent(element, classNames);
  }

  public final boolean hasAnnotation(ClassName className) {
    return DaggerElements.isAnnotationPresent(element, className);
  }

  @Override
  public final List<XAnnotation> getAllAnnotations() {
    return element.getAnnotationMirrors().stream()
        .map(mirror -> new XAnnotation(env, mirror))
        .collect(Collectors.toList());
  }

  @Override
  public final XAnnotation getAnnotation(ClassName className) {
    return DaggerElements.getAnnotationMirror(element, className)
        .map(annotationMirror -> new XAnnotation(env, annotationMirror))
        .orElse(null);
  }

  public final String getSimpleName() {
    return element.getSimpleName().toString();
  }

  final XProcessingEnv env() {
    return env;
  }
}
