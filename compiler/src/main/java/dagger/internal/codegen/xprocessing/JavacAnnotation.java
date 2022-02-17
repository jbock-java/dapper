package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.AnnotationMirrors;
import io.jbock.auto.common.MoreTypes;
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
        .map(e -> JavacAnnotationValue.create(env, e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }

  @Override
  public XAnnotationValue getAnnotationValue(String methodName) {
    return getAnnotationValues().stream()
        .filter(it -> it.name().equals(methodName))
        .findFirst()
        .orElseThrow();
  }

  @Override
  public List<XAnnotationValue> getAsAnnotationValueList(String methodName) {
    return getAnnotationValue(methodName).asAnnotationValueList();
  }

  @Override
  public XType getType() {
    return new JavacDeclaredType(env, mirror.getAnnotationType());
  }

  @Override
  public AnnotationMirror toJavac() {
    return mirror;
  }

  @Override
  public String getName() {
    return mirror.getAnnotationType().asElement().getSimpleName().toString();
  }

  @Override
  public String getQualifiedName() {
    return MoreTypes.asTypeElement(mirror.getAnnotationType()).getQualifiedName().toString();
  }

  @Override
  public List<XType> getAsTypeList(String methodName) {
    return getAnnotationValue(methodName).asTypeList();
  }

  @Override
  public String getAsString(String methodName) {
    return getAnnotationValue(methodName).asString();
  }
}
