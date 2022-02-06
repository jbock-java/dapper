package dagger.internal.codegen.xprocessing;

import java.util.Objects;
import javax.lang.model.element.Element;

class JavacEnumEntry extends JavacElement {

  private final XEnumTypeElement enumTypeElement;
  private final String name;

  JavacEnumEntry(XProcessingEnv env, Element element, XEnumTypeElement enumTypeElement) {
    super(env, element);
    this.enumTypeElement = enumTypeElement;
    this.name = element.getSimpleName().toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    JavacEnumEntry that = (JavacEnumEntry) o;
    return enumTypeElement.equals(that.enumTypeElement) && name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), enumTypeElement, name);
  }
}
