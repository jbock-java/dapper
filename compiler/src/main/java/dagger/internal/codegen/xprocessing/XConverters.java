package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public class XConverters {

  public static XTypeElement toXProcessing(TypeElement typeElement, XProcessingEnv env) {
    return env.wrapTypeElement(typeElement);
  }

  public static XExecutableElement toXProcessing(ExecutableElement executableElement, XProcessingEnv env) {
    return env.wrapExecutableElement(executableElement);
  }

  public static XVariableElement toXProcessing(VariableElement variableElement, XProcessingEnv env) {
    return env.wrapVariableElement(variableElement);
  }

  public static XAnnotation toXProcessing(AnnotationMirror mirror, XProcessingEnv env) {
    return new JavacAnnotation(env, mirror);
  }

  public static XElement toXProcessing(Element element, XProcessingEnv env) {
    if (element instanceof TypeElement) {
      return toXProcessing((TypeElement) element, env);
    }
    if (element instanceof ExecutableElement) {
      return toXProcessing((ExecutableElement) element, env);
    }
    if (element instanceof VariableElement) {
      return toXProcessing((VariableElement) element, env);
    }
    throw new IllegalArgumentException("unexpected kind: " + element.getKind());
  }

  /**
   * Returns an {@code XType} for the given {@code TypeMirror}.
   *
   * Warning: This method should be used only for migration since the returned {@code XType} will be
   * missing nullability information. Calling {@code XType#nullability} on these types will result in
   * an {@code IllegalStateException}.
   */
  public static XType toXProcessing(TypeMirror type, XProcessingEnv env) {
    return env.wrap(type);
  }

  public static ExecutableElement toJavac(XExecutableElement element) {
    return element.toJavac();
  }
}
