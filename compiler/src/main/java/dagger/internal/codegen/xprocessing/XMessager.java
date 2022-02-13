package dagger.internal.codegen.xprocessing;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;

public interface XMessager {

  void printMessage(
      Diagnostic.Kind kind,
      String msg);

  void printMessage(
      Diagnostic.Kind kind,
      String msg,
      XElement element);

  void printMessage(
      Diagnostic.Kind kind,
      String msg,
      XElement element,
      XAnnotation annotation);

  void printMessage(
      Diagnostic.Kind kind,
      String msg,
      XElement element,
      XAnnotation annotation,
      XAnnotationValue annotationValue);

  Messager toJavac();
}
