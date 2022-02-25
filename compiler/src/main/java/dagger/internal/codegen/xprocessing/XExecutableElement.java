package dagger.internal.codegen.xprocessing;

import java.util.List;
import javax.lang.model.element.ExecutableElement;

public interface XExecutableElement extends XElement {

  @Override
  ExecutableElement toJavac();

  XTypeElement getEnclosingElement();

  List<XExecutableParameterElement> getParameters();

  List<XType> getThrownTypes();

  /**
   * Returns true if this method receives a vararg parameter.
   */
  boolean isVarArgs();

  /** The type representation of the method where more type parameters might be resolved. */
  XExecutableType getExecutableType();

  /**
   * Returns the method as if it is declared in {@code other}.
   *
   * This is specifically useful if you have a method that has type arguments and there is a
   * subclass {@code other} where type arguments are specified to actual types.
   */
  XExecutableType asMemberOf(XType other);

  @Override
  String toString();
}
