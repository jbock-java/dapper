package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.ClassName;
import java.util.List;
import javax.lang.model.element.TypeElement;

public interface XTypeElement extends XMemberContainer {

  List<XMethodElement> getAllMethods();

  List<XMethodElement> getAllNonPrivateInstanceMethods();

  @Override
  TypeElement toJavac();

  String getQualifiedName();

  ClassName getClassName();

  XType getType();

  XTypeElement getEnclosingTypeElement();

  boolean isClass();

  boolean isInterface();

  List<XConstructorElement> getConstructors();

  List<XTypeElement> getSuperInterfaceElements();

  XType superType();

  List<XMethodElement> getDeclaredMethods();

  String getPackageName();

  List<XTypeElement> getEnclosedTypeElements();
}
