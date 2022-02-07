package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreElements;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;

class JavacConstructorElement extends JavacExecutableElement implements XConstructorElement {
  JavacConstructorElement(ExecutableElement element, XProcessingEnv env) {
    this(element, env.wrapTypeElement(MoreElements.asType(element.getEnclosingElement())), env);
  }

  JavacConstructorElement(ExecutableElement element, XTypeElement containing, XProcessingEnv env) {
    super(element, containing, env);
  }

  @Override
  public List<XExecutableParameterElement> getParameters() {
    return toJavac().getParameters().stream()
        .map(variable -> new JavacMethodParameter(env(), this, containing(), variable))
        .collect(Collectors.toList());
  }
}
