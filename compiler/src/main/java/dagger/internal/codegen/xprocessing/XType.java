package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreTypes;
import java.util.List;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

public abstract class XType {

  private final XProcessingEnv env;
  private final TypeMirror typeMirror;

  XType(XProcessingEnv env, TypeMirror typeMirror) {
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

  public abstract List<XType> getTypeArguments();

  public final boolean isSameType(XType other) {
    return env.toJavac().getTypeUtils().isSameType(typeMirror, other.typeMirror);
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
    return env.toJavac().getTypeUtils().isAssignable(other.typeMirror, typeMirror);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    XType xType = (XType) o;
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
