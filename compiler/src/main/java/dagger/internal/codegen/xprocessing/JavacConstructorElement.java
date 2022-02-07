package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.ExecutableElement;

class JavacConstructorElement extends JavacExecutableElement implements XConstructorElement {
  public JavacConstructorElement(ExecutableElement element, XProcessingEnv env) {
    super(element, env);
  }

}
