package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.ExecutableElement;

public class XExecutableElement extends XElement {
  XExecutableElement(ExecutableElement element, XProcessingEnv env) {
    super(element, env);
  }
}
