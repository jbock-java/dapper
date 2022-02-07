package dagger.internal.codegen.xprocessing;

import javax.lang.model.type.ExecutableType;

class JavacMethodType extends JavacExecutableType implements XMethodType {

  JavacMethodType(
      XProcessingEnv env,
      XMethodElement element,
      ExecutableType executableType) {
    super(env, element, executableType);
  }

  @Override
  public XType getReturnType() {
    return env().wrap(executableType().getReturnType());
  }
}
