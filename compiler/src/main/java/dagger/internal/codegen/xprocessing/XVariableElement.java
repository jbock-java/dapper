package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public class XVariableElement extends JavacElement {

  private final VariableElement variableElement;

  public XVariableElement(VariableElement element, XProcessingEnv env) {
    super(env, element);
    this.variableElement = element;
  }

  public VariableElement toJavac() {
    return variableElement;
  }

  public XType getType() {
    return env().wrap(variableElement.asType());
  }
}
