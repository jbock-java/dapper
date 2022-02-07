package dagger.internal.codegen.xprocessing;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public interface XExecutableElement extends XElement {

  @Override
  ExecutableElement toJavac();

  XMemberContainer getEnclosingElement();

  List<XExecutableParameterElement> getParameters();

  List<? extends TypeMirror> getThrownTypes();

  @Override
  String toString();
}
