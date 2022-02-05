package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreTypes;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public class XMethodElement extends XExecutableElement {
  XMethodElement(ExecutableElement element, XProcessingEnv env) {
    super(element, env);
  }

  public XMethodType getExecutableType() {
    TypeMirror asMemberOf = env().toJavac().getTypeUtils()
        .asMemberOf(MoreTypes.asDeclared(toJavac().getEnclosingElement().asType()), toJavac());
    return new XMethodType(env(), this, MoreTypes.asExecutable(asMemberOf));
  }

  public String getName() {
    return toJavac().getSimpleName().toString();
  }
}
