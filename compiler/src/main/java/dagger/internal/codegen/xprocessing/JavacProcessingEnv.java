package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.TypeName;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class JavacProcessingEnv extends XProcessingEnv {

  private static final Map<String, TypeKind> PRIMITIVE_TYPES = getPrimitiveTypes();

  private final ProcessingEnvironment delegate;

  private final XTypeElementStore typeElementStore;

  private static Map<String, TypeKind> getPrimitiveTypes() {
    Map<String, TypeKind> types = new HashMap<>();
    for (TypeKind kind : TypeKind.values()) {
      types.put(kind.name().toLowerCase(Locale.US), kind);
    }
    return types;
  }

  public JavacProcessingEnv(ProcessingEnvironment delegate) {
    this.delegate = delegate;
    this.typeElementStore = new XTypeElementStore(
        qName -> delegate.getElementUtils().getTypeElement(qName),
        it -> it.getQualifiedName().toString(),
        it -> JavacTypeElement.create(this, it));
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
    return new JavacMessager(delegate.getMessager());
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
    TypeElement typeElement = delegate.getElementUtils().getTypeElement(qName);
    if (typeElement == null) {
      return null;
    }
    return wrapTypeElement(typeElement);
  }

  @Override
  public XType getDeclaredType(XTypeElement type2, XType... types) {
    JavacTypeElement type = (JavacTypeElement) type2;
    TypeMirror[] args = Arrays.stream(types)
        .map(XType::toJavac)
        .toArray(TypeMirror[]::new);
    return wrap(delegate.getTypeUtils().getDeclaredType(type.toJavac(), args));
  }

  @Override
  public XFiler getFiler() {
    return new JavacFiler(this, delegate.getFiler());
  }

  @Override
  public XTypeElement requireTypeElement(TypeName typeName) {
    return requireTypeElement(typeName.toString());
  }

  @Override
  XTypeElement wrapTypeElement(TypeElement typeElement) {
    return typeElementStore.get(typeElement);
  }

  void clearCache() {
    typeElementStore.clear();
  }
}
