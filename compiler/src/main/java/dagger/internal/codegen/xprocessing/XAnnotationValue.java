package dagger.internal.codegen.xprocessing;

import java.util.List;
import javax.lang.model.element.AnnotationValue;

/** This wraps information about an argument in an annotation. */
public interface XAnnotationValue {

  /** The property name. */
  String getName();

  /**
   * The value set on the annotation property, or the default value if it was not explicitly set.
   *
   * <p>Possible types are: - Primitives (Boolean, Byte, Int, Long, Float, Double) - String -
   * XEnumEntry - XAnnotation - XType - List of {@code XAnnotationValue}
   */
  Object getValue();

  AnnotationValue toJavac();

  /** Returns the value as another XAnnotation. */
  XAnnotation asAnnotation();

  /** Returns the value as a [XEnumEntry]. */
  XEnumEntry asEnum();

  /** Returns the value a list of {@code XAnnotationValue}. */
  List<XAnnotationValue> asAnnotationValueList();

  /** Returns the value a list of {@code String}. */
  List<String> asStringList();

  /** Returns the value as a list of {@code XType}. */
  List<XType> asTypeList();

  /** Returns the value as a {@code XType}. */
  XType asType();

  /** Returns the value as a {@code String}. */
  String asString();

  /** Returns the value as a {@code Byte}. */
  Byte asByte();

  /** Returns the value as a {@code Double}. */
  Double asDouble();

  /** Returns the value as a {@code Float}. */
  Float asFloat();

  /** Returns the value as a {@code Long}. */
  Long asLong();

  /** Returns the value as a {@code Short}. */
  Short asShort();

  /** Returns the value as a {@code Boolean}. */
  boolean asBoolean();
}
