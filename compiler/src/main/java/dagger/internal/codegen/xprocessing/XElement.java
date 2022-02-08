package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.Element;

public interface XElement extends XAnnotated, XHasModifiers {

  Element toJavac();

  boolean isMethod();

  boolean isMethodParameter();
}
