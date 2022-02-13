package dagger.internal.codegen.xprocessing;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;

public class JavacMessager implements XMessager {

  private final Messager messager;

  JavacMessager(Messager messager) {
    this.messager = messager;
  }

  @Override
  public void printMessage(
      Diagnostic.Kind kind,
      String msg) {
    messager.printMessage(kind, msg);
  }

  @Override
  public void printMessage(
      Diagnostic.Kind kind,
      String msg,
      XElement element) {
    messager.printMessage(kind, msg, element.toJavac());
  }

  @Override
  public void printMessage(
      Diagnostic.Kind kind,
      String msg,
      XElement element,
      XAnnotation annotation) {
    messager.printMessage(kind, msg, element.toJavac(), annotation.toJavac());
  }

  @Override
  public void printMessage(
      Diagnostic.Kind kind,
      String msg,
      XElement element,
      XAnnotation annotation,
      XAnnotationValue annotationValue) {
    messager.printMessage(kind, msg, element.toJavac(), annotation.toJavac(), annotationValue.toJavac());
  }

  @Override
  public Messager toJavac() {
    return messager;
  }
}
