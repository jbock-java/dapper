package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.VariableElement;

class JavacFieldElement extends JavacVariableElement {
  
  JavacFieldElement(XProcessingEnv env, XTypeElement containing, VariableElement element) {
    super(env, containing, element);
  }
}
