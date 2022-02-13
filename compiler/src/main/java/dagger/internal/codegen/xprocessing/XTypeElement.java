package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.ClassName;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.TypeElement;

public interface XTypeElement extends XMemberContainer {

  List<XMethodElement> getAllMethods();

  List<XMethodElement> getAllNonPrivateInstanceMethods();

  @Override
  TypeElement toJavac();

  String getQualifiedName();

  /**
   * SimpleName of the type converted to String.
   */
  String getName();


  ClassName getClassName();

  XType getType();

  XTypeElement getEnclosingTypeElement();

  /**
   * The super type of this element if it represents a class.
   */
  XType getSuperType();

  boolean isClass();

  boolean isInterface();

  /**
   * Returns the list of constructors in this type element
   */
  List<XConstructorElement> getConstructors();

  List<XTypeElement> getSuperInterfaceElements();

  XType superType();

  /**
   * methods declared in this type
   *  includes all instance/static methods in this
   */
  List<XMethodElement> getDeclaredMethods();

  /**
   * Fields declared in this type
   *  includes all instance/static fields in this
   */
  List<XFieldElement> getDeclaredFields();

  String getPackageName();

  List<XTypeElement> getEnclosedTypeElements();

  List<XElement> getEnclosedElements();
}
