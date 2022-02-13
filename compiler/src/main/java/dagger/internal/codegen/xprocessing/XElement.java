package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.Element;

public interface XElement extends XAnnotated, XHasModifiers {

  Element toJavac();

  static boolean isTypeElement(XElement element) {
    return element instanceof XTypeElement;
  }

  static boolean isConstructor(XElement element) {
    return element instanceof XConstructorElement;
  }

  static boolean isMethod(XElement element) {
    return element instanceof XMethodElement;
  }

  static boolean isVariableElement(XElement element) {
    return element instanceof XVariableElement;
  }

  static boolean isField(XElement element) {
    return element instanceof XFieldElement;
  }

  static boolean isMethodParameter(XElement element) {
    return element instanceof XExecutableParameterElement;
  }
}
