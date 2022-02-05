package dagger.internal.codegen.xprocessing;

import javax.lang.model.type.ArrayType;

class JavacArrayType extends XType {

  private final ArrayType arrayType;

  JavacArrayType(XProcessingEnv env, ArrayType typeMirror) {
    super(env, typeMirror);
    this.arrayType = typeMirror;
  }
}
