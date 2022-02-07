package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreElements;
import javax.lang.model.element.ExecutableElement;

class JavacConstructorElement extends JavacExecutableElement implements XConstructorElement {
  JavacConstructorElement(ExecutableElement element, XProcessingEnv env) {
    this(element, env.wrapTypeElement(MoreElements.asType(element.getEnclosingElement())), env);
  }

  JavacConstructorElement(ExecutableElement element, XTypeElement containing, XProcessingEnv env) {
    super(element, containing, env);
  }
}
