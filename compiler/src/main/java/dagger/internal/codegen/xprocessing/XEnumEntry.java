package dagger.internal.codegen.xprocessing;

public interface XEnumEntry extends XElement {

  String getName();

  XEnumTypeElement enumTypeElement();
}
