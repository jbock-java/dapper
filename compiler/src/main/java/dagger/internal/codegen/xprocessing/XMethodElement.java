package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.ExecutableElement;

public class XMethodElement extends XExecutableElement {
  XMethodElement(ExecutableElement element, XProcessingEnv env) {
    super(element, env);
  }

  public String getName() {
    return toJavac().getSimpleName().toString();
  }
}
