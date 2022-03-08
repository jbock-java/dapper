package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.Element;

public interface XElement extends XAnnotated, XHasModifiers {

  Element toJavac();

  XElement getEnclosingElement();

  /**
   * Returns the closest member container.
   * Could be the element if it's itself a member container.
   */
  XMemberContainer getClosestMemberContainer();

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

  /**
   * Returns true if all types referenced by this element are valid, i.e. resolvable.
   */
  boolean validate();
}
