package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.VariableElement;

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
}
