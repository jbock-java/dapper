package dagger.internal.codegen.xprocessing;

public interface XHasModifiers {

  boolean isAbstract();

  boolean isPrivate();

  boolean isPublic();

  boolean isProtected();

  boolean isStatic();
}
