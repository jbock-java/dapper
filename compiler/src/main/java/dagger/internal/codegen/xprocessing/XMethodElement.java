package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.ExecutableElement;

public class XMethodElement extends XExecutableElement {
  public XMethodElement(ExecutableElement element, XProcessingEnv env) {
    super(element, env);
  }
}
