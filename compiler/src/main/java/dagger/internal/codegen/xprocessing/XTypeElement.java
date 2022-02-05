package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreElements;
import io.jbock.javapoet.ClassName;
import java.util.List;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

public class XTypeElement extends XElement {

  private final TypeElement typeElement;

  public XTypeElement(TypeElement element, XProcessingEnv env) {
    super(element, env);
    this.typeElement = element;
  }

  public List<XMethodElement> getAllMethods() {
    return null;
  }

  public List<XMethodElement> getAllNonPrivateInstanceMethods() {
    return null;
  }

  @Override
  public TypeElement toJavac() {
    return typeElement;
  }

  public Name getQualifiedName() {
    return typeElement.getQualifiedName();
  }

  public ClassName getClassName() {
    return ClassName.get(typeElement);
  }

  public TypeMirror getType() {
    return typeElement.asType();
  }

  public XTypeElement getEnclosingTypeElement() {
    try {
      return new XTypeElement(MoreElements.asType(typeElement.getEnclosingElement()), env());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public boolean isClass() {
    return typeElement.getKind() == ElementKind.CLASS;
  }

  public boolean isInterface() {
    return typeElement.getKind() == ElementKind.INTERFACE;
  }
}
