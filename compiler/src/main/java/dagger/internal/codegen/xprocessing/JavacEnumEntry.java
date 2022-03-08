package dagger.internal.codegen.xprocessing;

import java.util.Objects;
import javax.lang.model.element.Element;

class JavacEnumEntry extends JavacElement implements XEnumEntry {

  private final XEnumTypeElement enclosingElement;
  private final String name;

  JavacEnumEntry( //
      XProcessingEnv env, //
      Element entryElement, //
      XEnumTypeElement enclosingElement) {
    super(env, entryElement);
    this.enclosingElement = enclosingElement;
    this.name = entryElement.getSimpleName().toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    JavacEnumEntry that = (JavacEnumEntry) o;
    return enclosingElement.equals(that.enclosingElement) && name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enclosingElement, name);
  }

  @Override
  public XMemberContainer getClosestMemberContainer() {
    return enclosingElement;
  }

  @Override
  public String getName() {
    return element.getSimpleName().toString();
  }

  @Override
  public XEnumTypeElement getEnumTypeElement() {
    return enclosingElement;
  }

  @Override
  public XEnumTypeElement getEnclosingElement() {
    return null;
  }
}
