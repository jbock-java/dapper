package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

public class XConverters {

  public static XTypeElement toXProcessing(TypeElement typeElement, XProcessingEnv processingEnv) {
    return new XTypeElement(typeElement, processingEnv);
  }

  public static XExecutableElement toXProcessing(ExecutableElement executableElement, XProcessingEnv processingEnv) {
    if (executableElement.getKind() == ElementKind.CONSTRUCTOR) {
      return new XConstructorElement(executableElement, processingEnv);
    } else {
      return new XMethodElement(executableElement, processingEnv);
    }
  }

  public static XVariableElement toXProcessing(VariableElement variableElement, XProcessingEnv processingEnv) {
    return new XVariableElement(variableElement, processingEnv);
  }

  public static XElement toXProcessing(Element element, XProcessingEnv processingEnv) {
    if (element instanceof TypeElement) {
      return toXProcessing((TypeElement) element, processingEnv);
    }
    if (element instanceof ExecutableElement) {
      return toXProcessing((ExecutableElement) element, processingEnv);
    }
    if (element instanceof VariableElement) {
      return toXProcessing((VariableElement) element, processingEnv);
    }
    throw new IllegalArgumentException("unexpected kind: " + element.getKind());
  }
}
