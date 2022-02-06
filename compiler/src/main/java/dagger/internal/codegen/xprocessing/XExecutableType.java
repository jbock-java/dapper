package dagger.internal.codegen.xprocessing;

import java.util.List;

/**
 * Represents a type information for a method or constructor.
 *
 * It is not an XType as it does not represent a class or primitive.
 */
public interface XExecutableType {

  /** Parameter types of the method or constructor. */
  public List<XType> getParameterTypes();
}
