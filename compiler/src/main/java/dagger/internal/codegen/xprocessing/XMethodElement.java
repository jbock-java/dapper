package dagger.internal.codegen.xprocessing;

public interface XMethodElement extends XExecutableElement {

  XMethodType getExecutableType();

  String getName();

  String getJvmName();

  boolean isAccessibleFrom(String packageName);

  boolean isStaticInterfaceMethod();

  /**
   * Returns true if this method has the default modifier.
   */
  boolean isJavaDefault();

  XMethodElement copyTo(XTypeElement newContainer);

  XType getReturnType();

  /**
   * Returns the method as if it is declared in {@code other}.
   *
   * This is specifically useful if you have a method that has type arguments and there is a
   * subclass {@code other} where type arguments are specified to actual types.
   */
  XMethodType asMemberOf(XType other);

  /**
   * Returns {@code true} if this method overrides the {@code other} method when this method is viewed as
   * member of the {@code owner}.
   */
  boolean overrides(XMethodElement other, XTypeElement owner);
}
