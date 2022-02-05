package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.AnnotationMirrors;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;

public class XAnnotation {

  private final XProcessingEnv env;
  private final AnnotationMirror mirror;

  XAnnotation(XProcessingEnv env, AnnotationMirror mirror) {
    this.env = env;
    this.mirror = mirror;
  }

  List<XAnnotationValue> getAnnotationValues() {
    return AnnotationMirrors.getAnnotationValuesWithDefaults(mirror)
        .entrySet().stream()
        .map(e -> {
          ExecutableElement executableElement = e.getKey();
          AnnotationValue annotationValue = e.getValue();
          return new XAnnotationValue(env, executableElement, annotationValue);
        }).collect(Collectors.toList());
  }

  public XType getType() {
    return new JavacDeclaredType(env, mirror.getAnnotationType());
  }

  public AnnotationMirror toJavac() {
    return mirror;
  }
}
