package dagger.internal.codegen.xprocessing;

public interface XEnumEntry extends XElement {

  String getName();

  XEnumTypeElement getEnumTypeElement();

  /** The parent enum type declaration that holds all entries for this enum type.. */
  XEnumTypeElement getEnclosingElement();
}
