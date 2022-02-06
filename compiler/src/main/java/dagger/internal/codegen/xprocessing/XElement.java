package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.ClassName;
import javax.lang.model.element.Element;

public interface XElement extends XAnnotated, XHasModifiers {

  Element toJavac();

  boolean hasAnyOf(Iterable<ClassName> classNames);

  String getSimpleName();
}
