package dagger.internal.codegen.xprocessing;

import dagger.internal.codegen.langmodel.DaggerElements;
import io.jbock.javapoet.ClassName;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

class JavacElement implements XElement {

  private final XProcessingEnv env;
  private final Element element;

  JavacElement(XProcessingEnv env, Element element) {
    this.env = env;
    this.element = element;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    XElement xElement = (XElement) o;
    return element.equals(xElement.toJavac());
  }

  @Override
  public String toString() {
    return element.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(element);
  }

  @Override
  public Element toJavac() {
    return element;
  }

  @Override
  public final boolean isPublic() {
    return element.getModifiers().contains(Modifier.PUBLIC);
  }

  @Override
  public final boolean isProtected() {
    return element.getModifiers().contains(Modifier.PROTECTED);
  }

  @Override
  public boolean isStatic() {
    return element.getModifiers().contains(Modifier.STATIC);
  }

  @Override
  public final boolean isPrivate() {
    return element.getModifiers().contains(Modifier.PRIVATE);
  }

  @Override
  public final boolean isAbstract() {
    return element.getModifiers().contains(Modifier.ABSTRACT);
  }

  @Override
  public final boolean hasAnnotation(ClassName className) {
    return DaggerElements.isAnnotationPresent(element, className);
  }

  @Override
  public final List<XAnnotation> getAllAnnotations() {
    return element.getAnnotationMirrors().stream()
        .map(mirror -> new JavacAnnotation(env, mirror))
        .collect(Collectors.toList());
  }

  @Override
  public final XAnnotation getAnnotation(ClassName className) {
    return DaggerElements.getAnnotationMirror(element, className)
        .map(annotationMirror -> new JavacAnnotation(env, annotationMirror))
        .orElse(null);
  }

  final XProcessingEnv env() {
    return env;
  }
}
