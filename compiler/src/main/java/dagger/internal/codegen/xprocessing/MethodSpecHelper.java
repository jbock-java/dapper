package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.MethodSpec;
import io.jbock.javapoet.ParameterSpec;
import java.util.List;
import javax.lang.model.element.Modifier;

public final class MethodSpecHelper {

  /**
   * Creates an overriding {@code MethodSpec} for the given {@code XMethodElement} where:
   *
   * <ul>
   *   <li>parameter names are copied from KotlinMetadata when available
   *   <li>{@code Override} annotation is added and other annotations are dropped
   *   <li>thrown types are copied if the backing element is from java
   * </ul>
   */
  public static MethodSpec.Builder overriding(XMethodElement elm, XType owner) {
    XMethodType asMember = elm.asMemberOf(owner);
    return overriding(elm, asMember);
  }

  private static MethodSpec.Builder overriding(
      XMethodElement executableElement, XMethodType resolvedType, Modifier... paramModifiers) {
    MethodSpec.Builder result = MethodSpec.methodBuilder(executableElement.getJvmName());
    result.addTypeVariables(resolvedType.getTypeVariableNames());
    List<XType> parameterTypes = resolvedType.getParameterTypes();
    for (int index = 0; index < parameterTypes.size(); index++) {
      XType paramType = parameterTypes.get(index);
      result.addParameter(
          ParameterSpec.builder(
                  paramType.getTypeName(),
                  executableElement.getParameters().get(index).getName(),
                  paramModifiers)
              .build());
    }
    if (executableElement.isPublic()) {
      result.addModifiers(Modifier.PUBLIC);
    } else if (executableElement.isProtected()) {
      result.addModifiers(Modifier.PROTECTED);
    }
    result.addAnnotation(Override.class);
    result.varargs(executableElement.isVarArgs());
    executableElement.getThrownTypes().forEach(it -> result.addException(it.getTypeName()));
    result.returns(resolvedType.getReturnType().getTypeName());
    return result;
  }

  private MethodSpecHelper() {
  }
}
