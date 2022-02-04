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

/**
 * This wraps information about an argument in an annotation.
 */
public class XAnnotationValue {

  private static class VisitorData {
    final XProcessingEnv env;
    final ExecutableElement method;

    VisitorData(XProcessingEnv env, ExecutableElement method) {
      this.env = env;
      this.method = method;
    }
  }

  private final ExecutableElement method;
  private final Supplier<Object> valueProvider;
  private final AnnotationValue annotationValue;

  XAnnotationValue(
      XProcessingEnv env,
      ExecutableElement method,
      AnnotationValue annotationValue) {
    this.annotationValue = annotationValue;
    this.method = method;
    this.valueProvider = Suppliers.memoize(() ->
        UNWRAP_VISITOR.visit(annotationValue, new VisitorData(env, method)));
  }

  /**
   * The property name.
   */
  public String name() {
    return method.getSimpleName().toString();
  }

  /**
   * The value set on the annotation property, or the default value if it was not explicitly set.
   *
   * Possible types are:
   * - Primitives (Boolean, Byte, Int, Long, Float, Double)
   * - String
   * - XEnumEntry
   * - XAnnotation
   * - XType
   * - List of [XAnnotationValue]
   */
  public Object value() {
    return valueProvider.get();
  }

  private static final SimpleAnnotationValueVisitor8<Object, VisitorData> UNWRAP_VISITOR = new SimpleAnnotationValueVisitor8<>() {
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
    public Object visitType(TypeMirror t, VisitorData visitorData) {
      if (t.getKind() == TypeKind.ERROR) {
        throw new TypeNotPresentException(t.toString(), null);
      }
      return new XType(t);
    }

    @Override
    public Object visitEnumConstant(VariableElement c, VisitorData data) {
      TypeMirror type = c.asType();
      if (type.getKind() == TypeKind.ERROR) {
        throw new TypeNotPresentException(type.toString(), null);
      }
      TypeElement enumTypeElement = MoreTypes.asTypeElement(type);
      return new JavacEnumEntry(
          data.env,
          c,
          new XEnumTypeElement(data.env, enumTypeElement));
    }

    @Override
    public Object visitAnnotation(AnnotationMirror a, VisitorData data) {
      return new XAnnotation(data.env, a);
    }

    @Override
    public Object visitArray(List<? extends AnnotationValue> vals, VisitorData data) {
      return vals.stream().map(it -> new XAnnotationValue(data.env, data.method, it) {
        @Override
        public Object value() {
          return it.accept(UNWRAP_VISITOR, data);
        }
      }).collect(Collectors.toList());
    }
  };

  public AnnotationValue toJavac() {
    return annotationValue;
  }
}
