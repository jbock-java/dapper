package dagger.internal.codegen.xprocessing;

public interface XFieldElement extends XVariableElement, XHasModifiers {

  XTypeElement getEnclosingElement();
}
