package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ArrayTypeName;
import io.jbock.javapoet.TypeName;
import java.util.Map;
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

public interface XProcessingEnv {

  static XProcessingEnv create(ProcessingEnvironment processingEnv) {
    return new JavacProcessingEnv(processingEnv);
  }

  XMessager getMessager();

  Elements getElementUtils();

  Types getTypeUtils();

  /** List of options passed into the annotation processor */
  Map<String, String> getOptions();

  default JavacType wrap(TypeMirror typeMirror) {
    switch (typeMirror.getKind()) {
      case ARRAY:
        return new JavacArrayType(this, MoreTypes.asArray(typeMirror));
      case DECLARED:
        return new JavacDeclaredType(this, MoreTypes.asDeclared(typeMirror));
      default:
        return new DefaultJavacType(this, typeMirror);
    }
  }

  XTypeElement wrapTypeElement(TypeElement typeElement);

  default XVariableElement wrapVariableElement(VariableElement element) {
    Element enclosingElement = element.getEnclosingElement();
    if (enclosingElement instanceof ExecutableElement) {
      XExecutableElement executableElement =
          wrapExecutableElement((ExecutableElement) enclosingElement);
      return executableElement.getParameters().stream()
          .filter(param -> param.toJavac().equals(element))
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      String.format("Unable to create variable element for %s", element)));
    }
    if (enclosingElement instanceof TypeElement) {
      return new JavacFieldElement(this, wrapTypeElement((TypeElement) enclosingElement), element);
    }
    throw new IllegalStateException(
        String.format("Unsupported enclosing type %s for %s", enclosingElement, element));
  }

  default XExecutableElement wrapExecutableElement(ExecutableElement element) {
    TypeElement enclosingType = MoreElements.asType(element.getEnclosingElement());
    if (element.getKind() == ElementKind.CONSTRUCTOR) {
      return new JavacConstructorElement(element, wrapTypeElement(enclosingType), this);
    }
    if (element.getKind() == ElementKind.METHOD) {
      return new JavacMethodElement(element, wrapTypeElement(enclosingType), this);
    }
    throw new IllegalStateException(
        String.format("Unsupported kind %s of executable element %s", element.getKind(), element));
  }

  ProcessingEnvironment toJavac();

  /**
   * Looks for the {@code XType} with the given qualified name and returns {@code null} if it does
   * not exist.
   */
  XType findType(String qName);

  default XType findType(TypeName typeName) {
    if (typeName instanceof ArrayTypeName) {
      throw new IllegalArgumentException("TODO");
    }
    return findType(typeName.toString());
  }

  /**
   * Looks for the {@code XTypeElement} with the given qualified name and returns {@code null} if it
   * does not exist.
   */
  XTypeElement findTypeElement(String qName);

  default XTypeElement findTypeElement(TypeName typeName) {
    return findTypeElement(typeName.toString());
  }

  /**
   * Returns the [XTypeElement] with the given qualified name or throws an exception if it does not
   * exist.
   */
  default XTypeElement requireTypeElement(String qName) {
    return Objects.requireNonNull(
        findTypeElement(qName), () -> String.format("Cannot find required type element %s", qName));
  }

  /** Returns an XType for the given type element with the type arguments specified as in types. */
  XType getDeclaredType(XTypeElement type, XType... types);

  XFiler getFiler();

  XTypeElement requireTypeElement(TypeName typeName);

  /**
   * Returns the XType with the given qualified name or throws an exception if it does not exist.
   */
  default XType requireType(String qName) {
    XType result = findType(qName);
    if (result == null) {
      throw new IllegalStateException(String.format("cannot find required type %s", qName));
    }
    return result;
  }

  default XType requireType(TypeName typeName) {
    XType result = findType(typeName);
    if (result == null) {
      throw new IllegalStateException(String.format("cannot find required type %s", typeName));
    }
    return result;
  }
}
