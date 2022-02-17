package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.TypeVariableName;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.type.ExecutableType;

abstract class JavacExecutableType implements XExecutableType {

  private final XProcessingEnv env;
  private final XExecutableElement element;
  private final ExecutableType executableType;

  JavacExecutableType(
      XProcessingEnv env,
      XExecutableElement element,
      ExecutableType executableType) {
    this.env = env;
    this.element = element;
    this.executableType = executableType;
  }

  /**
   * Returns the names of {@code TypeVariableName}s for this executable.
   */
  @Override
  public List<TypeVariableName> getTypeVariableNames() {
    return toJavac().getTypeVariables().stream()
        .map(TypeVariableName::get)
        .collect(Collectors.toList());
  }


  @Override
  public List<XType> getParameterTypes() {
    return toJavac().getParameterTypes().stream()
        .map(env()::wrap)
        .collect(Collectors.toList());
  }

  ExecutableType toJavac() {
    return executableType;
  }

  XProcessingEnv env() {
    return env;
  }
}
