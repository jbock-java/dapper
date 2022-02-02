package dagger.internal.codegen.xprocessing;

import java.util.List;
import java.util.Objects;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

public abstract class XElement {

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

  public boolean isPublic() {
    return element.getModifiers().contains(Modifier.PUBLIC);
  }

  public boolean isPrivate() {
    return element.getModifiers().contains(Modifier.PRIVATE);
  }

  public boolean isAbstract() {
    return element.getModifiers().contains(Modifier.ABSTRACT);
  }

  public abstract List<XVariableElement> getParameters();

  final XProcessingEnv env() {
    return env;
  }
}
