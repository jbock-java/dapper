package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.VariableElement;

class JavacFieldElement extends JavacVariableElement implements XFieldElement {

  JavacFieldElement(XProcessingEnv env, XTypeElement containing, VariableElement element) {
    super(env, containing, element);
  }

  @Override
  public XTypeElement getEnclosingElement() {
    return containing();
  }
}
