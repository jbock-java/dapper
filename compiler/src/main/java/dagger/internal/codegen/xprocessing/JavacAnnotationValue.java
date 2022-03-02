package dagger.internal.codegen.xprocessing;

import dagger.internal.codegen.base.Suppliers;
import io.jbock.auto.common.MoreTypes;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

class JavacAnnotationValue implements XAnnotationValue {

  private final ExecutableElement method;
  private final Supplier<Object> valueProvider;
  private final AnnotationValue annotationValue;

  private JavacAnnotationValue(
      ExecutableElement method, AnnotationValue annotationValue, Supplier<Object> valueProvider) {
    this.method = method;
    this.valueProvider = valueProvider;
    this.annotationValue = annotationValue;
  }

  static JavacAnnotationValue create(
      XProcessingEnv env, ExecutableElement method, AnnotationValue annotationValue) {
    return new JavacAnnotationValue(
        method,
        annotationValue,
        Suppliers.memoize(
            () -> UNWRAP_VISITOR.visit(annotationValue, new VisitorData(env, method))));
  }

  public String name() {
    return method.getSimpleName().toString();
  }

  public Object value() {
    return valueProvider.get();
  }

  public AnnotationValue toJavac() {
    return annotationValue;
  }

  @Override
  @SuppressWarnings("unchecked") // Values in a list are always wrapped in XAnnotationValue
  public List<XAnnotationValue> asAnnotationValueList() {
    return (List<XAnnotationValue>) value();
  }

  @Override
  public List<String> asStringList() {
    return asAnnotationValueList().stream()
        .map(XAnnotationValue::asString)
        .collect(Collectors.toList());
  }

  @Override
  public List<XType> asTypeList() {
    return asAnnotationValueList().stream()
        .map(XAnnotationValue::asType)
        .collect(Collectors.toList());
  }

  @Override
  public XType asType() {
    return (XType) valueProvider.get();
  }

  @Override
  public String asString() {
    return (String) valueProvider.get();
  }

  @Override
  public boolean asBoolean() {
    return (boolean) valueProvider.get();
  }

  private static final SimpleAnnotationValueVisitor8<Object, VisitorData> UNWRAP_VISITOR =
      new SimpleAnnotationValueVisitor8<>() {
        @Override
        public Object visitBoolean(boolean b, VisitorData visitorData) {
          return b;
        }

        @Override
        public Object visitByte(byte b, VisitorData visitorData) {
          return b;
        }

        @Override
        public Object visitChar(char c, VisitorData visitorData) {
          return c;
        }

        @Override
        public Object visitDouble(double d, VisitorData visitorData) {
          return d;
        }

        @Override
        public Object visitFloat(float f, VisitorData visitorData) {
          return f;
        }

        @Override
        public Object visitInt(int i, VisitorData visitorData) {
          return i;
        }

        @Override
        public Object visitLong(long i, VisitorData visitorData) {
          return i;
        }

        @Override
        public Object visitShort(short s, VisitorData visitorData) {
          return s;
        }

        @Override
        public Object visitString(String s, VisitorData visitorData) {
          if ("<error>".equals(s)) {
            throw new TypeNotPresentException(s, null);
          } else {
            return s;
          }
        }

        @Override
        public Object visitType(TypeMirror t, VisitorData data) {
          if (t.getKind() == TypeKind.ERROR) {
            throw new TypeNotPresentException(t.toString(), null);
          }
          return data.env.wrap(t);
        }

        @Override
        public Object visitEnumConstant(VariableElement c, VisitorData data) {
          TypeMirror type = c.asType();
          if (type.getKind() == TypeKind.ERROR) {
            throw new TypeNotPresentException(type.toString(), null);
          }
          TypeElement enumTypeElement = MoreTypes.asTypeElement(type);
          return new JavacEnumEntry(
              data.env, c, new JavacEnumTypeElement(data.env, enumTypeElement));
        }

        @Override
        public Object visitAnnotation(AnnotationMirror a, VisitorData data) {
          return new JavacAnnotation(data.env, a);
        }

        @Override
        public Object visitArray(List<? extends AnnotationValue> vals, VisitorData data) {
          return vals.stream()
              .map(
                  it ->
                      new JavacAnnotationValue(
                          data.method,
                          it,
                          Suppliers.memoize(() -> it.accept(UNWRAP_VISITOR, data))))
              .collect(Collectors.toList());
        }
      };

  private static class VisitorData {
    final XProcessingEnv env;
    final ExecutableElement method;

    VisitorData(XProcessingEnv env, ExecutableElement method) {
      this.env = env;
      this.method = method;
    }
  }
}
