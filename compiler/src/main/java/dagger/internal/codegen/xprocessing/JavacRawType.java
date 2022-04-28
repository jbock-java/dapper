package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.TypeName;
import java.util.Objects;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

public class JavacRawType implements XRawType {
  private final TypeMirror erased;
  private final Types typeUtils;
  private final TypeName typeName;

  public JavacRawType(XProcessingEnv env, JavacType original) {
    erased = env.getTypeUtils().erasure(original.toJavac());
    typeUtils = env.getTypeUtils();
    typeName = TypeName.get(erased); // TODO safeTypeName
  }

  @Override
  public TypeName getTypeName() {
    return typeName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JavacRawType that = (JavacRawType) o;
    return typeName.equals(that.typeName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(typeName);
  }
}
