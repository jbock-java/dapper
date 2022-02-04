package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.TypeElement;

public class XEnumTypeElement extends XElement {
  XEnumTypeElement(XProcessingEnv env, TypeElement enumTypeElement) {
    super(enumTypeElement, env);
  }
}
