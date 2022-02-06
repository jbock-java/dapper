package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.TypeElement;

public class XEnumTypeElement extends JavacElement {
  XEnumTypeElement(XProcessingEnv env, TypeElement enumTypeElement) {
    super(env, enumTypeElement);
  }
}
