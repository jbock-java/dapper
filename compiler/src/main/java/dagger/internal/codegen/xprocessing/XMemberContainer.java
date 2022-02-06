package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.ClassName;

public interface XMemberContainer extends XElement {

  ClassName getClassName();

  XType getType();
}
