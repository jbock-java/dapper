package dagger.internal.codegen.xprocessing;

import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

public class XTypeElement extends XElement {

  public XTypeElement(TypeElement element, XProcessingEnv env) {
    super(element, env);
  }

  public List<XMethodElement> getAllMethods() {
    return null;
  }

  public List<XMethodElement> getAllNonPrivateInstanceMethods() {
    return null;
  }
}
