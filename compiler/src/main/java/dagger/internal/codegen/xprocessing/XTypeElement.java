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

  XMemberContainer getEnclosingElement();

  /**
   * SimpleName of the type converted to String.
   */
  String getName();


  ClassName getClassName();

  JavacDeclaredType getType();

  /** The XTypeElement that contains this XTypeElement if it is an inner class/interface. */
  XTypeElement getEnclosingTypeElement();

  /** The super interfaces implemented by this class. */
  List<XType> getSuperInterfaces();

  /**
   * The super type of this element if it represents a class.
   */
  XType getSuperType();

  /**
   * The direct super types of this element.
   *
   * See [JLS 4.10.2](https://docs.oracle.com/javase/specs/jls/se18/html/jls-4.html#jls-4.10.2)
   */
  List<XType> getSuperTypes();

  /**
   * The super class of this element if it represents a class.
   */
  XType getSuperClass();

  boolean isClass();

  boolean isInterface();

  /**
   * Returns {@code true} if this {@code XTypeElement} represents a Java
   * annotation type.
   */
  boolean isAnnotationClass();

  /**
   * Returns {@code true} if this {@code XTypeElement} is a nested class/interface.
   */
  boolean isNested();


  /**
   * Returns the list of constructors in this type element
   */
  List<XConstructorElement> getConstructors();

  List<XTypeElement> getSuperInterfaceElements();

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

  default boolean isKotlinObject() {
    return false;
  }

  default boolean isCompanionObject() {
    return false;
  }

  /**
   * Returns true if this XTypeElement represents a Kotlin data class
   */
  default boolean isDataClass(){
    return false;
  }
}
