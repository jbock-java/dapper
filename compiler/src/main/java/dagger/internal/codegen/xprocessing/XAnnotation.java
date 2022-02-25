package dagger.internal.codegen.xprocessing;

import java.util.List;
import javax.lang.model.element.AnnotationMirror;

public interface XAnnotation {

  List<XAnnotationValue> getAnnotationValues();

  XAnnotationValue getAnnotationValue(String methodName);

  /** Returns the value of the given methodName as a list of XAnnotationValue. */
  List<XAnnotationValue> getAsAnnotationValueList(String methodName);

  List<String> getAsStringList(String methodName);

  /**
   * The {@code XType} representing the annotation class.
   *
   * Accessing this requires resolving the type, and is thus more expensive that just accessing
   * {@link #getName()}.
   */
  XType getType();

  AnnotationMirror toJavac();

  /**
   * The simple name of the annotation class.
   */
  String getName();

  /**
   * The fully qualified name of the annotation class.
   * Accessing this forces the type to be resolved.
   */
  String getQualifiedName();

  /** Returns the value of the given {@code methodName} as a list of type references. */
  List<XType> getAsTypeList(String methodName);

  /** Returns the value of the given {@code methodName} as a {@code String}. */
  String getAsString(String methodName);
}
