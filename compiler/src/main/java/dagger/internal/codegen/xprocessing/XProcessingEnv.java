package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ArrayTypeName;
import io.jbock.javapoet.TypeName;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public abstract class XProcessingEnv {

  public static XProcessingEnv create(ProcessingEnvironment processingEnv) {
    return new JavacProcessingEnv(processingEnv);
  }

  public abstract XMessager getMessager();

  public abstract Elements getElementUtils();

  public abstract Types getTypeUtils();

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

  XVariableElement wrapVariableElement(VariableElement element) {
    Element enclosingElement = element.getEnclosingElement();
    if (enclosingElement instanceof ExecutableElement) {
      XExecutableElement executableElement = wrapExecutableElement((ExecutableElement) enclosingElement);
      return executableElement.getParameters().stream()
          .filter(param -> param.toJavac().equals(element))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(String.format("Unable to create variable element for %s", element)));
    }
    if (enclosingElement instanceof TypeElement) {
      return new JavacFieldElement(this, wrapTypeElement((TypeElement) enclosingElement), element);
    }
    throw new IllegalStateException(String.format("Unsupported enclosing type %s for %s", enclosingElement, element));
  }

  XExecutableElement wrapExecutableElement(ExecutableElement element) {
    TypeElement enclosingType = MoreElements.asType(element.getEnclosingElement());
    if (element.getKind() == ElementKind.CONSTRUCTOR) {
      return new JavacConstructorElement(element, wrapTypeElement(enclosingType), this);
    }
    if (element.getKind() == ElementKind.METHOD) {
      return new JavacMethodElement(element, wrapTypeElement(enclosingType), this);
    }
    throw new IllegalStateException(String.format("Unsupported kind %s of executable element %s", element.getKind(), element));
  }

  public abstract ProcessingEnvironment toJavac();

  /**
   * Looks for the {@code XType} with the given qualified name and returns {@code null} if it does not exist.
   */
  public abstract XType findType(String qName);

  public XType findType(TypeName typeName) {
    if (typeName instanceof ArrayTypeName) {
      throw new IllegalArgumentException("TODO");
    }
    return findType(typeName.toString());
  }

  /**
   * Looks for the {@code XTypeElement} with the given qualified name and returns {@code null} if it does not
   * exist.
   */
  public abstract XTypeElement findTypeElement(String qName);

  public XTypeElement findTypeElement(TypeName typeName) {
    return findTypeElement(typeName.toString());
  }

  public abstract XFiler getFiler();
}
