package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreTypes;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

abstract class JavacVariableElement extends JavacElement implements XVariableElement {

  private final XProcessingEnv env;
  private final XTypeElement containing;
  private final VariableElement element;

  JavacVariableElement(XProcessingEnv env, XTypeElement containing, VariableElement element) {
    super(env, element);
    this.env = env;
    this.containing = containing;
    this.element = element;
  }

  @Override
  public VariableElement toJavac() {
    return element;
  }

  @Override
  public XType getType() {
    return env.wrap(element.asType());
  }

  @Override
  public String getName() {
    return element.getSimpleName().toString();
  }

  XTypeElement containing() {
    return containing;
  }

  @Override
  public XType asMemberOf(XType other) {
    if (containing.getType().isSameType(other)) {
      return getType();
    }
    JavacDeclaredType otherDeclared = (JavacDeclaredType) other;
    TypeMirror typeMirror = MoreTypes.asMemberOf(env.getTypeUtils(), otherDeclared.typeMirror(), element);
    return env.wrap(typeMirror);
  }
}
