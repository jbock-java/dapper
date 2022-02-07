package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.AnnotationMirrors;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;

class JavacAnnotation implements XAnnotation {

  private final XProcessingEnv env;
  private final AnnotationMirror mirror;

  JavacAnnotation(XProcessingEnv env, AnnotationMirror mirror) {
    this.env = env;
    this.mirror = mirror;
  }

  @Override
  public List<XAnnotationValue> getAnnotationValues() {
    return AnnotationMirrors.getAnnotationValuesWithDefaults(mirror)
        .entrySet().stream()
        .map(e -> new XAnnotationValue(env, e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }

  @Override
  public XType getType() {
    return new JavacDeclaredType(env, mirror.getAnnotationType());
  }

  @Override
  public AnnotationMirror toJavac() {
    return mirror;
  }
}
