package dagger.internal.codegen.xprocessing;

import javax.lang.model.type.ExecutableType;

/**
 * Represents a type information for a method.
 *
 * It is not an XType as it does not represent a class or primitive.
 */
public interface XMethodType extends XExecutableType {

  /**
   * The return type of the method
   */
  XType getReturnType();

  ExecutableType toJavac();
}
