package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.VariableElement;

public class XVariableElement extends XElement {
  public XVariableElement(VariableElement element, XProcessingEnv env) {
    super(element, env);
  }
}
