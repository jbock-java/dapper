package dagger.internal.codegen.xprocessing;

public interface XConstructorElement extends XExecutableElement {

  XConstructorType getExecutableType();

  /**
   * Returns the constructor as if it is declared in {@code other}.
   *
   * This is specifically useful if you have a constructor that has type arguments and there is a
   * subclass {@code other} where type arguments are specified to actual types.
   */
  XConstructorType asMemberOf(XType other);
}
