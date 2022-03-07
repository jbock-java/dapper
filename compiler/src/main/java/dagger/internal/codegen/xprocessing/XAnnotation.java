package dagger.internal.codegen.xprocessing;

import java.util.List;
import java.util.stream.Collectors;
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
   * <p>Accessing this requires resolving the type, and is thus more expensive that just accessing
   * {@link #getName()}.
   */
  XType getType();

  AnnotationMirror toJavac();

  /** The simple name of the annotation class. */
  String getName();

  /**
   * The fully qualified name of the annotation class. Accessing this forces the type to be
   * resolved.
   */
  String getQualifiedName();

  /** Returns the value of the given {@code methodName} as a list of type references. */
  List<XType> getAsTypeList(String methodName);

  /** Returns the value of the given {@code methodName} as a {@code String}. */
  String getAsString(String methodName);

  /** Returns the value of the given {@code methodName} as a {@code boolean}. */
  boolean getAsBoolean(String methodName);

  /**
   * Returns the value of the given [methodName], throwing an exception if the method is not found
   * or if the given type [T] does not match the actual type.
   *
   * <p>This uses a non-reified type and takes in a Class so it is callable by Java users.
   *
   * <p>Note that non primitive types are wrapped by interfaces in order to allow them to be
   * represented by the process: - "Class" types are represented with [XType] - Annotations are
   * represented with [XAnnotation] - Enums are represented with [XEnumEntry]
   *
   * <p>For convenience, wrapper functions are provided for these types, eg [XAnnotation.getAsType]
   */
  @Deprecated
  static <T> T get(XAnnotation annotation, String methodName, Class<T> clazz) {
    XAnnotationValue argument = annotation.getAnnotationValue(methodName);

    Object value;
    if (argument.getValue() instanceof List) {
      // If the argument is for a list, unwrap each item in the list
      value =
          ((List<Object>) argument.getValue())
              .stream()
                  .map(it -> XAnnotationValue.class.cast(it).getValue())
                  .collect(Collectors.toList());
    } else {
      value = argument.getValue();
    }

    if (!clazz.isInstance(value)) {
      throw new IllegalStateException(
          String.format(
              "Value of %s of type %s cannot be cast to %s",
              methodName, value == null ? "null" : value.getClass(), clazz));
    }

    @SuppressWarnings("unchecked")
    T result = (T) value;
    return result;
  }
}
