package dagger.internal.codegen.xprocessing;

import javax.lang.model.element.AnnotationValue;

/**
 * This wraps information about an argument in an annotation.
 */
public interface XAnnotationValue {

  /**
   * The property name.
   */
  public String name();

  /**
   * The value set on the annotation property, or the default value if it was not explicitly set.
   *
   * Possible types are:
   * - Primitives (Boolean, Byte, Int, Long, Float, Double)
   * - String
   * - XEnumEntry
   * - XAnnotation
   * - XType
   * - List of {@code XAnnotationValue}
   */
  public Object value();

  AnnotationValue toJavac();
}
