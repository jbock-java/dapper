package dagger.internal.codegen.xprocessing;

import javax.lang.model.type.TypeMirror;

class DefaultJavacType extends XType {

  DefaultJavacType(XProcessingEnv env, TypeMirror typeMirror) {
    super(env, typeMirror);
  }
}
