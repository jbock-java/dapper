package dagger.internal.codegen.xprocessing;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class JavacProcessingEnv extends XProcessingEnv {

  private static final Map<String, TypeKind> PRIMITIVE_TYPES = getPrimitiveTypes();

  private final ProcessingEnvironment delegate;

  private static Map<String, TypeKind> getPrimitiveTypes() {
    Map<String, TypeKind> types = new HashMap<>();
    for (TypeKind kind : TypeKind.values()) {
      types.put(kind.name().toLowerCase(Locale.US), kind);
    }
    return types;
  }

  public JavacProcessingEnv(ProcessingEnvironment delegate) {
    this.delegate = delegate;
  }

  @Override
  public Elements getElementUtils() {
    return delegate.getElementUtils();
  }

  @Override
  public Types getTypeUtils() {
    return delegate.getTypeUtils();
  }

  @Override
  public XMessager getMessager() {
    return new XMessager(delegate.getMessager());
  }

  @Override
  public ProcessingEnvironment toJavac() {
    return delegate;
  }

  @Override
  public XType findType(String qName) {
    TypeKind primitiveKind = PRIMITIVE_TYPES.get(qName);
    if (primitiveKind != null) {
      return wrap(delegate.getTypeUtils().getPrimitiveType(primitiveKind));
    }
    return findTypeElement(qName).getType();
  }

  @Override
  public XTypeElement findTypeElement(String qName) {
    return wrapTypeElement(delegate.getElementUtils().getTypeElement(qName));
  }
}
