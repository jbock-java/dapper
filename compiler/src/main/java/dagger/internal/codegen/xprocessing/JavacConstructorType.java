package dagger.internal.codegen.xprocessing;

import javax.lang.model.type.ExecutableType;

class JavacConstructorType extends JavacExecutableType implements XConstructorType {

  JavacConstructorType(
      XProcessingEnv env,
      XConstructorElement element,
      ExecutableType executableType) {
    super(env, element, executableType);
  }
}
