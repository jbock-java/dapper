package dagger.internal.codegen.xprocessing;

public interface XExecutableParameterElement extends XVariableElement {

  /**
   * The enclosing {@link XExecutableElement} this parameter belongs to.
   */
  XExecutableElement getEnclosingElement();
}
