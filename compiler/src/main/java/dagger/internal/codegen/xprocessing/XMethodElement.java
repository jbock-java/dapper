package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ClassName;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
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
        .asMemberOf(MoreTypes.asDeclared(toJavac().getEnclosingElement().asType()), toJavac());
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
    Element enclosingElement = toJavac().getEnclosingElement();
    ClassName anObject = ClassName.get(MoreElements.asType(enclosingElement));
    return packageName.equals(anObject.packageName());
  }

  public boolean isStaticInterfaceMethod() {
    return isStatic() && toJavac().getEnclosingElement().getKind() == ElementKind.INTERFACE;
  }

  public XMethodElement copyTo(XTypeElement newContainer) {
    return new XMethodElement(toJavac(), newContainer, env());
  }
}
