package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreTypes;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

public abstract class XProcessingEnv {

  public static XProcessingEnv create(ProcessingEnvironment processingEnv) {
    return new JavacProcessingEnv(processingEnv);
  }

  public abstract XMessager getMessager();

  XType wrap(TypeMirror typeMirror) {
    switch (typeMirror.getKind()) {
      case ARRAY:
        return new JavacArrayType(this, MoreTypes.asArray(typeMirror));
      case DECLARED:
        return new JavacDeclaredType(this, MoreTypes.asDeclared(typeMirror));
      default:
        return new DefaultJavacType(this, typeMirror);
    }
  }

  public abstract ProcessingEnvironment toJavac();
}
