package dagger.internal.codegen.xprocessing;

import java.util.List;
import javax.lang.model.type.TypeMirror;

public interface XType {

  TypeMirror toJavac();

  XTypeElement getTypeElement();

  List<XType> getTypeArguments();

  boolean isSameType(XType other);

  boolean isVoid();

  /**
   * Returns {@code true} if this type can be assigned from {@code other}
   */
  boolean isAssignableFrom(XType other);
}
