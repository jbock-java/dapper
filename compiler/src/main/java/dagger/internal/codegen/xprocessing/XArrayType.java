package dagger.internal.codegen.xprocessing;

public interface XArrayType extends XType {

  /**
   * The type of elements in the Array
   */
  XType getComponentType();
}
