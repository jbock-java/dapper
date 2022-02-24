package dagger.internal.codegen.xprocessing;

import java.util.List;
import javax.lang.model.type.ArrayType;

class JavacArrayType extends JavacType implements XArrayType {

  private final ArrayType arrayType;

  JavacArrayType(XProcessingEnv env, ArrayType typeMirror) {
    super(env, typeMirror);
    this.arrayType = typeMirror;
  }

  @Override
  public List<XType> getTypeArguments() {
    return List.of();
  }

  @Override
  public XType getComponentType() {
    return env().wrap(arrayType.getComponentType());
  }
}
