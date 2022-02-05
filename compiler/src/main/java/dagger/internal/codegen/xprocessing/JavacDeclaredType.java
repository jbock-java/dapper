package dagger.internal.codegen.xprocessing;

import javax.lang.model.type.DeclaredType;

class JavacDeclaredType extends XType {

  private final DeclaredType declaredType;

  JavacDeclaredType(XProcessingEnv env, DeclaredType typeMirror) {
    super(env, typeMirror);
    this.declaredType = typeMirror;
  }
}
