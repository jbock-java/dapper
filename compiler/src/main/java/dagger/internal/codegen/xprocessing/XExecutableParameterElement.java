package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.VariableElement;

public class XExecutableParameterElement extends XVariableElement {
  XExecutableParameterElement(VariableElement element, XProcessingEnv env) {
    super(element, env);
  }
}
