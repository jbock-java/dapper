package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreTypes;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
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

  XTypeElement wrapTypeElement(TypeElement typeElement) {
    if (typeElement.getKind() == ElementKind.ENUM) {
      return new JavacEnumTypeElement(this, typeElement);
    } else {
      return new DefaultJavacTypeElement(this, typeElement);
    }
  }

  public abstract ProcessingEnvironment toJavac();
}
