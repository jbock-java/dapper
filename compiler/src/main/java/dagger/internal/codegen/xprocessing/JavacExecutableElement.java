package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreElements;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

abstract class JavacExecutableElement extends JavacElement implements XExecutableElement {

  private final ExecutableElement executableElement;
  private final Element enclosingElement;

  JavacExecutableElement(ExecutableElement element, Element enclosingElement, XProcessingEnv env) {
    super(env, element);
    this.executableElement = element;
    this.enclosingElement = enclosingElement;
  }

  JavacExecutableElement(ExecutableElement element, XProcessingEnv env) {
    this(element, element.getEnclosingElement(), env);
  }

  @Override
  public ExecutableElement toJavac() {
    return executableElement;
  }

  public XMemberContainer getEnclosingElement() {
    if (MoreElements.isType(enclosingElement)) {
      return env().wrapTypeElement(MoreElements.asType(enclosingElement));
    } else {
      return null;
    }
  }

  public final List<XExecutableParameterElement> getParameters() {
    return executableElement.getParameters().stream()
        .map(p -> new XExecutableParameterElement(p, env()))
        .collect(Collectors.toList());
  }

  public List<? extends TypeMirror> getThrownTypes() {
    return executableElement.getThrownTypes();
  }
}
