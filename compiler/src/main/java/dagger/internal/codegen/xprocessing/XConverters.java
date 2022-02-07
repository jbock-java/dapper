package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

public class XConverters {

  public static XTypeElement toXProcessing(TypeElement typeElement, XProcessingEnv env) {
    return env.wrapTypeElement(typeElement);
  }

  public static XExecutableElement toXProcessing(ExecutableElement executableElement, XProcessingEnv env) {
    if (executableElement.getKind() == ElementKind.CONSTRUCTOR) {
      return new JavacConstructorElement(executableElement, env);
    } else {
      return new JavacMethodElement(executableElement, env);
    }
  }

  public static XVariableElement toXProcessing(VariableElement variableElement, XProcessingEnv env) {
    return new XVariableElement(variableElement, env);
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
}
