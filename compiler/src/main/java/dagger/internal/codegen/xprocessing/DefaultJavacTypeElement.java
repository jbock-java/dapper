package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.TypeElement;

class DefaultJavacTypeElement extends JavacTypeElement {
  DefaultJavacTypeElement(XProcessingEnv env, TypeElement element) {
    super(env, element);
  }
}
