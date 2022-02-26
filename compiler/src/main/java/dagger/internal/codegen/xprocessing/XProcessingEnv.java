package dagger.internal.codegen.xprocessing;

import dagger.internal.codegen.javapoet.TypeNames;
import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ArrayTypeName;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.TypeName;
import java.util.Objects;
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

  JavacType wrap(TypeMirror typeMirror) {
    switch (typeMirror.getKind()) {
      case ARRAY:
        return new JavacArrayType(this, MoreTypes.asArray(typeMirror));
      case DECLARED:
        return new JavacDeclaredType(this, MoreTypes.asDeclared(typeMirror));
      default:
        return new DefaultJavacType(this, typeMirror);
    }
  }

  abstract XTypeElement wrapTypeElement(TypeElement typeElement);

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

  /**
   * Returns the [XTypeElement] with the given qualified name or throws an exception if it does
   * not exist.
   */
  public XTypeElement requireTypeElement(String qName) {
    return Objects.requireNonNull(findTypeElement(qName),
        () -> String.format("Cannot find required type element %s", qName));
  }

  /** Returns an XType for the given type element with the type arguments specified as in types. */
  public abstract XType getDeclaredType(XTypeElement type, XType... types);

  public abstract XFiler getFiler();

  public abstract XTypeElement requireTypeElement(TypeName typeName);
}
