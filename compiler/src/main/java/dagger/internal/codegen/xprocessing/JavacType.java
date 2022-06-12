package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.TypeName;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;

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

  public XProcessingEnv env() {
    return env;
  }

  public boolean isVoid() {
    return typeMirror.getKind() == TypeKind.VOID;
  }

  @Override
  public boolean isArray() {
    return this instanceof XArrayType;
  }

  @Override
  public TypeName getTypeName() {
    if (typeMirror.getKind() == TypeKind.NONE) {
      return ClassName.get("androidx.room.compiler.processing.error", "NotAType");
    }
    return TypeName.get(typeMirror);
  }

  @Override
  public List<XType> getSuperTypes() {
    List<? extends TypeMirror> superTypes = env.getTypeUtils().directSupertypes(typeMirror);
    return superTypes.stream().map(env::wrap).collect(Collectors.toList());
  }

  /** Returns {@code true} if this type can be assigned from {@code other} */
  public boolean isAssignableFrom(XType other) {
    return env.toJavac().getTypeUtils().isAssignable(other.toJavac(), typeMirror);
  }

  @Override
  public XRawType getRawType() {
    return new JavacRawType(env, this);
  }

  @Override
  public boolean isError() {
    return typeMirror.getKind() == TypeKind.ERROR;
  }

  @Override
  public boolean isNone() {
    return typeMirror.getKind() == TypeKind.NONE;
  }

  private static TypeMirror extendsBound(TypeMirror m) {
    return m.accept(
        new SimpleTypeVisitor8<TypeMirror, Void>() {
          @Override
          public TypeMirror visitWildcard(WildcardType type, Void ignored) {
            TypeMirror bound = type.getExtendsBound();
            if (bound != null) {
              return bound;
            }
            return type.getSuperBound();
          }
        },
        null);
  }

  @Override
  public XType extendsBound() {
    TypeMirror it = extendsBound(this.typeMirror);
    if (it == null) {
      return null;
    }
    return env.wrap(it);
  }

  @Override
  public XType boxed() {
    if (typeMirror.getKind().isPrimitive()) {
      return env.wrap(
          env.getTypeUtils().boxedClass(MoreTypes.asPrimitiveType(typeMirror)).asType());
    }
    if (typeMirror.getKind() == TypeKind.VOID) {
      return env.wrap(env.getElementUtils().getTypeElement("java.lang.Void").asType());
    }
    return this;
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
