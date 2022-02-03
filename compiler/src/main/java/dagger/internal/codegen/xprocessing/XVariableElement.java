package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public class XVariableElement extends XElement {

  private final VariableElement variableElement;

  public XVariableElement(VariableElement element, XProcessingEnv env) {
    super(element, env);
    this.variableElement = element;
  }

  public VariableElement toJavac() {
    return variableElement;
  }

  public TypeMirror getType() {
    return variableElement.asType();
  }
}
