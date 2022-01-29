package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.ExecutableElement;

public class XConstructorElement extends XExecutableElement {
  public XConstructorElement(ExecutableElement element, XProcessingEnv env) {
    super(element, env);
  }
}
