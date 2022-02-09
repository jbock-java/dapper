package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreTypes;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

abstract class JavacType implements XType {

  private final XProcessingEnv env;
  private final TypeMirror typeMirror;

  JavacType(XProcessingEnv env, TypeMirror typeMirror) {
    this.env = env;
    this.typeMirror = typeMirror;
  }

  public TypeMirror toJavac() {
    return typeMirror;
  }

  public final XTypeElement getTypeElement() {
    try {
      return env.wrapTypeElement(MoreTypes.asTypeElement(typeMirror));
    } catch (IllegalArgumentException notAnElement) {
      return null;
    }
  }

  public final boolean isSameType(XType other) {
    return env.toJavac().getTypeUtils().isSameType(typeMirror, other.toJavac());
  }

  XProcessingEnv env() {
    return env;
  }

  public boolean isVoid() {
    return typeMirror.getKind() == TypeKind.VOID;
  }

  /**
   * Returns {@code true} if this type can be assigned from {@code other}
   */
  public boolean isAssignableFrom(XType other) {
    return env.toJavac().getTypeUtils().isAssignable(other.toJavac(), typeMirror);
  }

  @Override
  public XType boxed() {
    if (typeMirror.getKind().isPrimitive()) {
      return env.wrap(env.getTypeUtils().boxedClass(MoreTypes.asPrimitiveType(typeMirror)).asType());
    }
    if (typeMirror.getKind() == TypeKind.VOID) {
      return env.wrap(env.getElementUtils().getTypeElement("java.lang.Void").asType());
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JavacType xType = (JavacType) o;
    return typeMirror.equals(xType.typeMirror);
  }

  @Override
  public int hashCode() {
    return typeMirror.hashCode();
  }

  @Override
  public String toString() {
    return typeMirror.toString();
  }
}
