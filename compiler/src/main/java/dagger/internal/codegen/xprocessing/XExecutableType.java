package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.TypeVariableName;
import java.util.List;

/**
 * Represents a type information for a method or constructor.
 *
 * It is not an XType as it does not represent a class or primitive.
 */
public interface XExecutableType {

  /** Parameter types of the method or constructor. */
  List<XType> getParameterTypes();

  /**
   * Returns the names of {@code TypeVariableName}s for this executable.
   */
  List<TypeVariableName> getTypeVariableNames();
}
