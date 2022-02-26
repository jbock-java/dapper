package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.VariableElement;

public interface XVariableElement extends XElement {

  VariableElement toJavac();

  XType getType();

  String getName();

  XType asMemberOf(XType other);
}
