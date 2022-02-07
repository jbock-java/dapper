package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.TypeElement;

class JavacEnumTypeElement extends JavacTypeElement implements XEnumTypeElement {
  JavacEnumTypeElement(XProcessingEnv env, TypeElement element) {
    super(env, element);
  }
}
