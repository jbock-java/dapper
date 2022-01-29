package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.Element;

public class XElement {

  private final XProcessingEnv env;
  private final Element element;

  public XElement(Element element, XProcessingEnv env) {
    this.env = env;
    this.element = element;
  }

  public Element toJavac() {
    return element;
  }
}
