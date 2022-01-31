package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.ExecutableElement;

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
}
