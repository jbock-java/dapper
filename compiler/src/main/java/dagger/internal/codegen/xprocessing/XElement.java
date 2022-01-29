package dagger.internal.codegen.xprocessing;

import java.util.Objects;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    XElement xElement = (XElement) o;
    return element.equals(xElement.element);
  }

  @Override
  public int hashCode() {
    return Objects.hash(element);
  }
}
