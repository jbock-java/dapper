package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

class JavacConstructorElement extends JavacExecutableElement implements XConstructorElement {
  JavacConstructorElement(ExecutableElement element, XProcessingEnv env) {
    this(element, env.wrapTypeElement(MoreElements.asType(element.getEnclosingElement())), env);
  }

  JavacConstructorElement(ExecutableElement element, XTypeElement containing, XProcessingEnv env) {
    super(element, (JavacTypeElement) containing, env);
  }

  @Override
  public List<XExecutableParameterElement> getParameters() {
    return toJavac().getParameters().stream()
        .map(variable -> new JavacMethodParameter(env(), this, containing(), variable))
        .collect(Collectors.toList());
  }

  @Override
  public XConstructorType getExecutableType() {
    TypeMirror asMemberOf = env().toJavac().getTypeUtils()
        .asMemberOf(MoreTypes.asDeclared(containing().toJavac().asType()), toJavac());
    return new JavacConstructorType(env(), this, MoreTypes.asExecutable(asMemberOf));
  }

  @Override
  public XConstructorType asMemberOf(XType other) {
    if (!(other instanceof JavacDeclaredType) || containing().getType().isSameType(other)) {
      return getExecutableType();
    }
    TypeMirror asMemberOf = env().toJavac().getTypeUtils().asMemberOf(((JavacDeclaredType) other).toJavac(), toJavac());
    return new JavacConstructorType(env(), this, MoreTypes.asExecutable(asMemberOf));
  }
}
