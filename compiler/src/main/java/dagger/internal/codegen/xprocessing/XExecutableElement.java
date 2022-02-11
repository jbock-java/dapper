package dagger.internal.codegen.xprocessing;

import java.util.List;
import javax.lang.model.element.ExecutableElement;

public interface XExecutableElement extends XElement {

  @Override
  ExecutableElement toJavac();

  XMemberContainer getEnclosingElement();

  List<XExecutableParameterElement> getParameters();

  List<XType> getThrownTypes();

  /**
   * Returns true if this method receives a vararg parameter.
   */
  boolean isVarArgs();

  @Override
  String toString();
}
