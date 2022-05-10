/*
 * Copyright (C) 2015 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen.langmodel;

import static dagger.internal.codegen.base.Preconditions.checkArgument;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static io.jbock.auto.common.MoreElements.getPackage;
import static io.jbock.auto.common.MoreTypes.asElement;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XType;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.TypeName;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleElementVisitor8;
import javax.lang.model.util.SimpleTypeVisitor8;

/**
 * Utility methods for determining whether a {@code TypeMirror type} or an {@code Element
 * element} is accessible given the rules outlined in <a
 * href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-6.html#jls-6.6">section 6.6 of the
 * Java Language Specification</a>.
 *
 * <p>This class only provides an approximation for accessibility. It does not always yield the same
 * result as the compiler, but will always err on the side of declaring something inaccessible. This
 * ensures that using this class will never result in generating code that will not compile.
 *
 * <p>Whenever compiler independence is not a requirement, the compiler-specific implementation of
 * this functionality should be preferred. For example, {@code
 * com.sun.source.util.Trees#isAccessible(com.sun.source.tree.Scope, TypeElement)} would be
 * preferable for {@code javac}.
 */
public final class Accessibility {
  /** Returns true if the given type can be referenced from any package. */
  public static boolean isTypePubliclyAccessible(XType type) {
    return isTypePubliclyAccessible(toJavac(type));
  }

  /** Returns true if the given type can be referenced from any package. */
  private static boolean isTypePubliclyAccessible(TypeMirror type) {
    return type.accept(new TypeAccessibilityVisitor(Optional.empty()), null);
  }

  /** Returns true if the given type can be referenced from code in the given package. */
  public static boolean isTypeAccessibleFrom(XType type, String packageName) {
    return isTypeAccessibleFrom(toJavac(type), packageName);
  }

  /** Returns true if the given type can be referenced from code in the given package. */
  private static boolean isTypeAccessibleFrom(TypeMirror type, String packageName) {
    return type.accept(new TypeAccessibilityVisitor(Optional.of(packageName)), null);
  }

  private static final class TypeAccessibilityVisitor extends SimpleTypeVisitor8<Boolean, Void> {
    private final Optional<String> packageName;

    TypeAccessibilityVisitor(Optional<String> packageName) {
      this.packageName = packageName;
    }

    boolean isAccessible(TypeMirror type) {
      return type.accept(this, null);
    }

    @Override
    public Boolean visitNoType(NoType type, Void p) {
      return true;
    }

    @Override
    public Boolean visitDeclared(DeclaredType type, Void p) {
      if (!isAccessible(type.getEnclosingType())) {
        // TODO(gak): investigate this check.  see comment in Binding
        return false;
      }
      if (!type.asElement().accept(new ElementAccessibilityVisitor(packageName), null)) {
        return false;
      }
      for (TypeMirror typeArgument : type.getTypeArguments()) {
        if (!isAccessible(typeArgument)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public Boolean visitArray(ArrayType type, Void p) {
      return type.getComponentType().accept(this, null);
    }

    @Override
    public Boolean visitPrimitive(PrimitiveType type, Void p) {
      return true;
    }

    @Override
    public Boolean visitNull(NullType type, Void p) {
      return true;
    }

    @Override
    public Boolean visitTypeVariable(TypeVariable type, Void p) {
      // a _reference_ to a type variable is always accessible
      return true;
    }

    @Override
    public Boolean visitWildcard(WildcardType type, Void p) {
      if (type.getExtendsBound() != null && !isAccessible(type.getExtendsBound())) {
        return false;
      }
      if (type.getSuperBound() != null && !isAccessible(type.getSuperBound())) {
        return false;
      }
      return true;
    }

    @Override
    protected Boolean defaultAction(TypeMirror type, Void p) {
      throw new IllegalArgumentException(
          String.format(
              "%s of kind %s should not be checked for accessibility", type, type.getKind()));
    }
  }

  /** Returns true if the given element can be referenced from any package. */
  public static boolean isElementPubliclyAccessible(XElement element) {
    return isElementPubliclyAccessible(toJavac(element));
  }

  /** Returns true if the given element can be referenced from any package. */
  private static boolean isElementPubliclyAccessible(Element element) {
    return element.accept(new ElementAccessibilityVisitor(Optional.empty()), null);
  }

  /** Returns true if the given element can be referenced from code in the given package. */
  // TODO(gak): account for protected
  // TODO(bcorso): account for kotlin srcs (package-private doesn't exist, internal does exist).
  public static boolean isElementAccessibleFrom(XElement element, String packageName) {
    return isElementAccessibleFrom(toJavac(element), packageName);
  }

  /** Returns true if the given element can be referenced from code in the given package. */
  // TODO(gak): account for protected
  private static boolean isElementAccessibleFrom(Element element, String packageName) {
    return element.accept(new ElementAccessibilityVisitor(Optional.of(packageName)), null);
  }

  /** Returns true if the given element can be referenced from other code in its own package. */
  public static boolean isElementAccessibleFromOwnPackage(XElement element) {
    return isElementAccessibleFromOwnPackage(toJavac(element));
  }

  /** Returns true if the given element can be referenced from other code in its own package. */
  private static boolean isElementAccessibleFromOwnPackage(Element element) {
    return isElementAccessibleFrom(element, getPackage(element).getQualifiedName().toString());
  }

  private static final class ElementAccessibilityVisitor
      extends SimpleElementVisitor8<Boolean, Void> {
    private final Optional<String> packageName;

    ElementAccessibilityVisitor(Optional<String> packageName) {
      this.packageName = packageName;
    }

    @Override
    public Boolean visitPackage(PackageElement element, Void p) {
      return true;
    }

    @Override
    public Boolean visitType(TypeElement element, Void p) {
      switch (element.getNestingKind()) {
        case MEMBER:
          return accessibleMember(element);
        case TOP_LEVEL:
          return accessibleModifiers(element);
        case ANONYMOUS:
        case LOCAL:
          return false;
      }
      throw new AssertionError();
    }

    boolean accessibleMember(Element element) {
      return element.getEnclosingElement().accept(this, null) && accessibleModifiers(element);
    }

    boolean accessibleModifiers(Element element) {
      if (element.getModifiers().contains(PUBLIC)) {
        return true;
      } else if (element.getModifiers().contains(PRIVATE)) {
        return false;
      }
      return packageName.isPresent()
          && getPackage(element).getQualifiedName().contentEquals(packageName.get());
    }

    @Override
    public Boolean visitTypeParameter(TypeParameterElement element, Void p) {
      throw new IllegalArgumentException(
          "It does not make sense to check the accessibility of a type parameter");
    }

    @Override
    public Boolean visitExecutable(ExecutableElement element, Void p) {
      return accessibleMember(element);
    }

    @Override
    public Boolean visitVariable(VariableElement element, Void p) {
      ElementKind kind = element.getKind();
      checkArgument(kind.isField(), "checking a variable that isn't a field: %s", kind);
      return accessibleMember(element);
    }
  }

  /** Returns true if the raw type of {@code type} is accessible from the given package. */
  public static boolean isRawTypeAccessible(XType type, String requestingPackage) {
    return isRawTypeAccessible(toJavac(type), requestingPackage);
  }

  /** Returns true if the raw type of {@code type} is accessible from the given package. */
  private static boolean isRawTypeAccessible(TypeMirror type, String requestingPackage) {
    return type.getKind() == TypeKind.DECLARED
        ? isElementAccessibleFrom(asElement(type), requestingPackage)
        : isTypeAccessibleFrom(type, requestingPackage);
  }

  /** Returns true if the raw type of {@code type} is accessible from any package. */
  public static boolean isRawTypePubliclyAccessible(XType type) {
    return isRawTypePubliclyAccessible(toJavac(type));
  }

  /** Returns true if the raw type of {@code type} is accessible from any package. */
  private static boolean isRawTypePubliclyAccessible(TypeMirror type) {
    return type.getKind() == TypeKind.DECLARED
        ? isElementPubliclyAccessible(asElement(type))
        : isTypePubliclyAccessible(type);
  }

  /**
   * Returns an accessible type in {@code requestingClass}'s package based on {@code type}:
   *
   * <ul>
   *   <li>If {@code type} is accessible from the package, returns it.
   *   <li>If not, but {@code type}'s raw type is accessible from the package, returns the raw type.
   *   <li>Otherwise returns {@code Object}.
   * </ul>
   */
  public static XType accessibleType(
      XType type, ClassName requestingClass, XProcessingEnv processingEnv) {
    if (isTypeAccessibleFrom(type, requestingClass.packageName())) {
      return type;
    } else if (isDeclared(type) && isRawTypeAccessible(type, requestingClass.packageName())) {
      return processingEnv.getDeclaredType(type.getTypeElement());
    } else {
      return processingEnv.requireType(TypeName.OBJECT);
    }
  }

  private Accessibility() {}
}
