package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.VariableElement;

class JavacMethodParameter extends JavacVariableElement implements XExecutableParameterElement {

  private final XExecutableElement enclosingMethodElement;

  JavacMethodParameter(
      XProcessingEnv env,
      XExecutableElement enclosingMethodElement,
      XTypeElement containing,
      VariableElement element) {
    super(env, containing, element);
    this.enclosingMethodElement = enclosingMethodElement;
  }

  @Override
  public XExecutableElement getEnclosingMethodElement() {
    return enclosingMethodElement;
  }
}
