package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ClassName;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

public class XMethodElement extends XExecutableElement {

  private final XTypeElement containing;

  XMethodElement(ExecutableElement element, XProcessingEnv env) {
    this(element, new XTypeElement(MoreElements.asType(element.getEnclosingElement()), env), env);
  }

  XMethodElement(ExecutableElement element, XTypeElement containing, XProcessingEnv env) {
    super(element, env);
    this.containing = containing;
  }

  public XMethodType getExecutableType() {
    TypeMirror asMemberOf = env().toJavac().getTypeUtils()
        .asMemberOf(MoreTypes.asDeclared(containing.toJavac().asType()), toJavac());
    return new XMethodType(env(), this, MoreTypes.asExecutable(asMemberOf));
  }

  public String getName() {
    return toJavac().getSimpleName().toString();
  }

  public boolean isAccessibleFrom(String packageName) {
    if (isPublic() || isProtected()) {
      return true;
    }
    if (isPrivate()) {
      return false;
    }
    // check package
    Element enclosingElement = containing.toJavac();
    ClassName anObject = ClassName.get(MoreElements.asType(enclosingElement));
    return packageName.equals(anObject.packageName());
  }

  public boolean isStaticInterfaceMethod() {
    return isStatic() && containing.toJavac().getKind() == ElementKind.INTERFACE;
  }

  public XMethodElement copyTo(XTypeElement newContainer) {
    return new XMethodElement(toJavac(), newContainer, env());
  }

  public XType getReturnType() {
    TypeMirror asMember = env().toJavac().getTypeUtils()
        .asMemberOf(MoreTypes.asDeclared(containing.toJavac().asType()), toJavac());
    ExecutableType asExec = MoreTypes.asExecutable(asMember);
    return env().wrap(asExec.getReturnType());
  }

  /**
   * Returns the method as if it is declared in [other].
   *
   * This is specifically useful if you have a method that has type arguments and there is a
   * subclass {@code other} where type arguments are specified to actual types.
   */
  public XMethodType asMemberOf(XType other) {
    if (!(other instanceof JavacDeclaredType) || containing.getType().isSameType(other)) {
      return getExecutableType();
    }
    TypeMirror asMemberOf = env().toJavac().getTypeUtils().asMemberOf(((JavacDeclaredType) other).toJavac(), toJavac());
    return new XMethodType(env(), this, MoreTypes.asExecutable(asMemberOf));
  }
}
