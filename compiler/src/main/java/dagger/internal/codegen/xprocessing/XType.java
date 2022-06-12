package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.TypeName;
import java.util.List;
import javax.lang.model.type.TypeMirror;

public interface XType {

  TypeMirror toJavac();

  /**
   * The XTypeElement that represents this type.
   *
   * Note that it might be null if the type is not backed by a type element (e.g. if it is a
   * primitive, wildcard etc)
   */
  XTypeElement getTypeElement();

  List<XType> getTypeArguments();

  TypeName getTypeName();

  /**
   * The resolved types of the super classes/interfaces of this type.
   */
  List<XType> getSuperTypes();

  boolean isSameType(XType other);

  boolean isVoid();

  boolean isArray();

  /**
   * Returns boxed version of this type if it is a primitive or itself if it is not a primitive
   * type.
   */
  XType boxed();

  /**
   * Returns {@code true} if this type can be assigned from {@code other}
   */
  boolean isAssignableFrom(XType other);

  /** Returns the rawType of this type. */
  XRawType getRawType();

  static boolean isVoid(XType type) {
    return type.isVoid();
  }

  static boolean isArray(XType type) {
    return type.isArray();
  }

  /**
   * Returns true if this is an error type.
   */
  boolean isError();

  XProcessingEnv env();

  /**
   * Returns `true` if this is the None type.
   */
   boolean isNone();

  /**
   * If this is a wildcard with an extends bound, returns that bounded typed.
   */
  XType extendsBound();
}
