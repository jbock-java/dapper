package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreTypes;
import java.util.List;
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
      return new XTypeElement(MoreTypes.asTypeElement(typeMirror), env);
    } catch (IllegalArgumentException notAnElement) {
      return null;
    }
  }

  public abstract List<XType> getTypeArguments();

  XProcessingEnv env() {
    return env;
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
}
