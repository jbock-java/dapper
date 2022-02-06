package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreElements;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public class XExecutableElement extends JavacElement {

  private final ExecutableElement executableElement;
  private final Element enclosingElement;

  XExecutableElement(ExecutableElement element, XProcessingEnv env) {
    super(env, element);
    this.executableElement = element;
    this.enclosingElement = executableElement.getEnclosingElement();
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

  @Override
  public String toString() {
    return executableElement.toString();
  }
}
