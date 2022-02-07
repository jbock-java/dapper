package dagger.internal.codegen.xprocessing;

public interface XMethodElement extends XExecutableElement {

  XMethodType getExecutableType();

  String getName();

  boolean isAccessibleFrom(String packageName);

  boolean isStaticInterfaceMethod();

  XMethodElement copyTo(XTypeElement newContainer);

  XType getReturnType();

  /**
   * Returns the method as if it is declared in {@code other}.
   *
   * This is specifically useful if you have a method that has type arguments and there is a
   * subclass {@code other} where type arguments are specified to actual types.
   */
  XMethodType asMemberOf(XType other);
}
