package dagger.internal.codegen.xprocessing;

import java.util.List;
import javax.lang.model.type.TypeMirror;

class DefaultJavacType extends XType {

  DefaultJavacType(XProcessingEnv env, TypeMirror typeMirror) {
    super(env, typeMirror);
  }

  @Override
  public List<XType> getTypeArguments() {
    return List.of();
  }
}
