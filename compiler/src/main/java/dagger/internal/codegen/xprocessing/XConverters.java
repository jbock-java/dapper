package dagger.internal.codegen.xprocessing;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

public class XConverters {

  public static XTypeElement toXProcessing(TypeElement typeElement, XProcessingEnv env) {
    return env.wrapTypeElement(typeElement);
  }

  public static XExecutableElement toXProcessing(
      ExecutableElement executableElement, XProcessingEnv env) {
    return env.wrapExecutableElement(executableElement);
  }

  public static XVariableElement toXProcessing(
      VariableElement variableElement, XProcessingEnv env) {
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
    throw new IllegalStateException(
        String.format(
            "Don't know how to convert element of type '%s' to a XElement", element.getClass()));
  }

  /**
   * Returns an {@code XType} for the given {@code TypeMirror}.
   *
   * <p>Warning: This method should be used only for migration since the returned {@code XType} will
   * be missing nullability information. Calling {@code XType#nullability} on these types will
   * result in an {@code IllegalStateException}.
   */
  public static XType toXProcessing(TypeMirror type, XProcessingEnv env) {
    return env.wrap(type);
  }

  public static ExecutableElement toJavac(XExecutableElement element) {
    return element.toJavac();
  }

  public static TypeElement toJavac(XTypeElement element) {
    return element.toJavac();
  }

  public static Element toJavac(XElement element) {
    return element.toJavac();
  }

  public static VariableElement toJavac(XVariableElement element) {
    return element.toJavac();
  }

  public static AnnotationValue toJavac(XAnnotationValue xAnnotationValue) {
    return xAnnotationValue.toJavac();
  }

  public static ExecutableType toJavac(XMethodType methodType) {
    return methodType.toJavac();
  }

  public static AnnotationMirror toJavac(XAnnotation element) {
    return element.toJavac();
  }

  public static TypeMirror toJavac(XType type) {
    return type.toJavac();
  }

  public static Messager toJavac(XMessager messager) {
    return messager.toJavac();
  }

  public static Filer toJavac(XFiler filer) {
    return filer.toJavac();
  }

  public static RoundEnvironment toJavac(XRoundEnv roundEnv) {
    return roundEnv.toJavac();
  }

  public static ProcessingEnvironment toJavac(XProcessingEnv env) {
    return env.toJavac();
  }

  public static XProcessingEnv getProcessingEnv(XType type) {
    return type.env();
  }

  public static XProcessingEnv getProcessingEnv(XElement element) {
    return element.env();
  }

  public static XProcessingEnv getProcessingEnv(XAnnotation annotation) {
    return annotation.env();
  }
}
