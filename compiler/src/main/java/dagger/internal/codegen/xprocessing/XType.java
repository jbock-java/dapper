package dagger.internal.codegen.xprocessing;

import javax.lang.model.type.TypeMirror;

public class XType {

  private final TypeMirror typeMirror;

  XType(TypeMirror typeMirror) {
    this.typeMirror = typeMirror;
  }

  public TypeMirror toJavac() {
    return typeMirror;
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
