package dagger.internal.codegen.xprocessing;

import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public class XExecutableElement extends XElement {

  private final ExecutableElement executableElement;

  XExecutableElement(ExecutableElement element, XProcessingEnv env) {
    super(element, env);
    this.executableElement = element;
  }

  @Override
  public ExecutableElement toJavac() {
    return executableElement;
  }

  public final List<XVariableElement> getParameters() {
    return executableElement.getParameters().stream()
        .map(p -> new XVariableElement(p, env()))
        .collect(Collectors.toList());
  }

  public List<? extends TypeMirror> getThrownTypes() {
    return executableElement.getThrownTypes();
  }
}
