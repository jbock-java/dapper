package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreElements;
import javax.lang.model.element.Element;

public class ElementExtKt {

  public static XTypeElement getEnclosingType(Element element, XProcessingEnv env) {
    if (MoreElements.isType(element.getEnclosingElement())) {
      return env.wrapTypeElement(MoreElements.asType(element.getEnclosingElement()));
    } else {
      return null;
    }
  }
}
