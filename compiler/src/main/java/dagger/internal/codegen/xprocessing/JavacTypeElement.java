package dagger.internal.codegen.xprocessing;

import dagger.internal.codegen.base.Util;
import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ClassName;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

abstract class JavacTypeElement extends JavacElement implements XTypeElement {

  private final TypeElement typeElement;

  JavacTypeElement(XProcessingEnv env, TypeElement element) {
    super(env, element);
    this.typeElement = element;
  }

  @Override
  public List<XMethodElement> getAllMethods() {
    return collectAllMethods();
  }

  @Override
  public List<XMethodElement> getAllNonPrivateInstanceMethods() {
    return getAllMethods().stream()
        .filter(m -> !m.isPrivate() && !m.isStatic())
        .collect(Collectors.toList());
  }

  private List<XMethodElement> collectAllMethods() {
    // group methods by name for faster override checks
    MethodsByName methodsByName = new MethodsByName(this);
    methodsByName.collectAllMethodsByName(this);
    // Yield all non-overridden methods
    return methodsByName.methodsByName.values().stream().flatMap(methodSet -> {
      if (methodSet.size() == 1) {
        // There's only one method with this name, so it can't be overridden
        return Stream.of(Util.getOnlyElement(methodSet));
      } else {
        // There are multiple methods with the same name, so we must check for overridden
        // methods. The order of the methods should guarantee that any potentially
        // overridden method comes first in the list, so we only need to check each method
        // against subsequent methods.
        List<XMethodElement> methods = List.copyOf(methodSet);
        Set<XMethodElement> overridden = new LinkedHashSet<>();
        forEachIndexed:
        for (int i = 0; i < methods.size(); i++) {
          XMethodElement methodOne = methods.get(i);
          for (int j = i + 1; j < methods.size(); j++) {
            XMethodElement methodTwo = methods.get(j);
            if (MoreElements.overrides(methodTwo.toJavac(), methodOne.toJavac(), typeElement, env().toJavac().getTypeUtils())) {
              overridden.add(methodOne);
              break forEachIndexed;
            }
          }
        }
        return methods.stream().filter(m -> !overridden.contains(m));
      }
    }).collect(Collectors.toList());
  }

  private static class MethodsByName {
    private final Map<String, Set<XMethodElement>> methodsByName = new LinkedHashMap<>();
    private final Set<XTypeElement> visitedInterfaces = new LinkedHashSet<>();
    private final XTypeElement xTypeElement;

    private MethodsByName(XTypeElement xTypeElement) {
      this.xTypeElement = xTypeElement;
    }

    void collectAllMethodsByName(XTypeElement type) {
      // First, visit all super interface methods.
      for (XTypeElement it : type.getSuperInterfaceElements()) {
        // Skip if we've already visited the methods in this interface.
        if (visitedInterfaces.add(it)) {
          collectAllMethodsByName(it);
        }
      }
      // Next, visit all super class methods.
      XType superType = type.superType();
      if (superType != null) {
        XTypeElement superTypeTypeElement = superType.getTypeElement();
        if (superTypeTypeElement != null) {
          collectAllMethodsByName(superTypeTypeElement);
        }
      }
      // Finally, visit all methods declared in this type.
      if (type == xTypeElement) {
        for (XMethodElement declaredMethod : type.getDeclaredMethods()) {
          methodsByName.merge(declaredMethod.getName(), Set.of(declaredMethod), Util::mutableUnion);
        }
      } else {
        type.getDeclaredMethods().stream()
            .filter(m -> m.isAccessibleFrom(type.getPackageName()))
            .filter(m -> !m.isStaticInterfaceMethod())
            .map(m -> m.copyTo(xTypeElement))
            .forEach(m -> methodsByName.merge(m.getName(), Set.of(m), Util::mutableUnion));
      }
    }
  }

  @Override
  public TypeElement toJavac() {
    return typeElement;
  }

  @Override
  public String getQualifiedName() {
    return typeElement.getQualifiedName().toString();
  }

  @Override
  public String getName() {
    return typeElement.getSimpleName().toString();
  }

  @Override
  public ClassName getClassName() {
    return ClassName.get(typeElement);
  }

  @Override
  public JavacDeclaredType getType() {
    return (JavacDeclaredType) env().wrap(typeElement.asType());
  }

  @Override
  public XTypeElement getEnclosingTypeElement() {
    return ElementExtKt.getEnclosingType(element, env());
  }

  @Override
  public boolean isClass() {
    return typeElement.getKind() == ElementKind.CLASS;
  }

  @Override
  public boolean isInterface() {
    return typeElement.getKind() == ElementKind.INTERFACE;
  }

  @Override
  public boolean isAnnotationClass() {
    return typeElement.getKind() == ElementKind.ANNOTATION_TYPE;
  }

  @Override
  public boolean isNested() {
    return MoreElements.isType(typeElement.getEnclosingElement());
  }

  @Override
  public List<XConstructorElement> getConstructors() {
    return ElementFilter.constructorsIn(typeElement.getEnclosedElements()).stream()
        .map(c -> new JavacConstructorElement(c, env()))
        .collect(Collectors.toList());
  }

  @Override
  public List<XTypeElement> getSuperInterfaceElements() {
    return typeElement.getInterfaces().stream()
        .map(it -> env().wrapTypeElement(MoreTypes.asTypeElement(it)))
        .collect(Collectors.toList());
  }

  @Override
  public XType superType() {
    TypeMirror superclass = typeElement.getSuperclass();
    if (superclass.getKind().equals(TypeKind.NONE)) {
      return null;
    }
    return env().wrap(superclass);
  }

  @Override
  public List<XMethodElement> getDeclaredMethods() {
    return ElementFilter.methodsIn(typeElement.getEnclosedElements()).stream()
        .map(it -> new JavacMethodElement(it, env()))
        .collect(Collectors.toList());
  }

  @Override
  public List<XFieldElement> getDeclaredFields() {
    return ElementFilter.fieldsIn(typeElement.getEnclosedElements()).stream()
        .filter(it -> it.getKind() != ElementKind.ENUM_CONSTANT)
        .map(it ->
            new JavacFieldElement(
                env(),
                this,
                it
            )).collect(Collectors.toList());
  }

  @Override
  public List<XElement> getEnclosedElements() {
    List<XElement> result = new ArrayList<>();
    result.addAll(getEnclosedTypeElements());
    result.addAll(getDeclaredFields());
    result.addAll(getConstructors());
    result.addAll(getDeclaredMethods());
    return result;
  }

  @Override
  public XType getSuperType() {
    // javac models non-existing types as TypeKind.NONE but we prefer to make it nullable.
    // just makes more sense and safer as we don't need to check for none.

    // The result value is a JavacType instead of JavacDeclaredType to gracefully handle
    // cases where super is an error type.
    TypeMirror superClass = typeElement.getSuperclass();
    if (superClass.getKind() == TypeKind.NONE) {
      return null;
    } else {
      return env().wrap(superClass);
    }
  }

  @Override
  public String getPackageName() {
    return MoreElements.getPackage(toJavac()).getQualifiedName().toString();
  }

  @Override
  public List<XTypeElement> getEnclosedTypeElements() {
    return ElementFilter.typesIn(typeElement.getEnclosedElements())
        .stream().map(it -> env().wrapTypeElement(it))
        .collect(Collectors.toList());
  }

  static JavacTypeElement create(
      JavacProcessingEnv env,
      TypeElement typeElement) {
    if (typeElement.getKind() == ElementKind.ENUM) {
      return new JavacEnumTypeElement(env, typeElement);
    } else {
      return new DefaultJavacTypeElement(env, typeElement);
    }
  }

  @Override
  public XMemberContainer getEnclosingElement() {
    return getEnclosingTypeElement();
  }

  @Override
  public XMemberContainer getClosestMemberContainer() {
    return this;
  }
}
