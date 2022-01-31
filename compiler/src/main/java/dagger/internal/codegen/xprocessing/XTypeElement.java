package dagger.internal.codegen.xprocessing;

import java.util.List;
import javax.lang.model.element.TypeElement;

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
}
