/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.internal.codegen.binding;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.binding.SourceFiles.simpleVariableName;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnyAnnotationPresent;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.Equivalence;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.model.BindingKind;
import dagger.model.Key;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** A type that a component needs an instance of. */
public final class ComponentRequirement {

  private final ComponentRequirement.Kind kind;
  private final Equivalence.Wrapper<TypeMirror> wrappedType;
  private final Optional<ComponentRequirement.NullPolicy> overrideNullPolicy;
  private final Optional<Key> key;
  private final String variableName;

  ComponentRequirement(
      ComponentRequirement.Kind kind,
      Equivalence.Wrapper<TypeMirror> wrappedType,
      Optional<ComponentRequirement.NullPolicy> overrideNullPolicy,
      Optional<Key> key,
      String variableName) {
    this.kind = requireNonNull(kind);
    this.wrappedType = requireNonNull(wrappedType);
    this.overrideNullPolicy = requireNonNull(overrideNullPolicy);
    this.key = requireNonNull(key);
    this.variableName = requireNonNull(variableName);
  }


  /** The kind of the {@link ComponentRequirement}. */
  public enum Kind {
    /** A type listed in the component's {@code dependencies} attribute. */
    DEPENDENCY,

    /** A type listed in the component or subcomponent's {@code modules} attribute. */
    MODULE,

    /**
     * An object that is passed to a builder's {@link dagger.BindsInstance @BindsInstance} method.
     */
    BOUND_INSTANCE,
    ;

    public boolean isBoundInstance() {
      return equals(BOUND_INSTANCE);
    }

    public boolean isModule() {
      return equals(MODULE);
    }
  }

  /** The kind of requirement. */
  public ComponentRequirement.Kind kind() {
    return kind;
  }

  /** Returns true if this is a {@link Kind#BOUND_INSTANCE} requirement. */
  // TODO(ronshapiro): consider removing this and inlining the usages
  boolean isBoundInstance() {
    return kind().isBoundInstance();
  }

  /**
   * The type of the instance the component must have, wrapped so that requirements can be used as
   * value types.
   */
  public Equivalence.Wrapper<TypeMirror> wrappedType() {
    return wrappedType;
  }

  /** The type of the instance the component must have. */
  public TypeMirror type() {
    return wrappedType().get();
  }

  /** The element associated with the type of this requirement. */
  public TypeElement typeElement() {
    return MoreTypes.asTypeElement(type());
  }

  /** The action a component builder should take if it {@code null} is passed. */
  public enum NullPolicy {
    /** Make a new instance. */
    NEW,
    /** Throw an exception. */
    THROW,
    /** Allow use of null values. */
    ALLOW,
  }

  /**
   * An override for the requirement's null policy. If set, this is used as the null policy instead
   * of the default behavior in {@link #nullPolicy}.
   *
   * <p>Some implementations' null policy can be determined upon construction (e.g., for binding
   * instances), but others' require Elements which must wait until {@link #nullPolicy} is called.
   */
  Optional<ComponentRequirement.NullPolicy> overrideNullPolicy() {
    return overrideNullPolicy;
  }

  /** The requirement's null policy. */
  public NullPolicy nullPolicy(DaggerElements elements) {
    if (overrideNullPolicy().isPresent()) {
      return overrideNullPolicy().get();
    }
    switch (kind()) {
      case MODULE:
        return componentCanMakeNewInstances(typeElement())
            ? NullPolicy.NEW
            : requiresAPassedInstance(elements) ? NullPolicy.THROW : NullPolicy.ALLOW;
      case DEPENDENCY:
      case BOUND_INSTANCE:
        return NullPolicy.THROW;
    }
    throw new AssertionError();
  }

  /**
   * Returns true if the passed {@link ComponentRequirement} requires a passed instance in order to
   * be used within a component.
   */
  public boolean requiresAPassedInstance(DaggerElements elements) {
    if (!kind().isModule()) {
      // Bound instances and dependencies always require the user to provide an instance.
      return true;
    }
    return requiresModuleInstance(elements)
        && !componentCanMakeNewInstances(typeElement());
  }

  /**
   * Returns {@code true} if an instance is needed for this (module) requirement.
   *
   * <p>An instance is only needed if there is a binding method on the module that is neither {@code
   * abstract} nor {@code static}; if all bindings are one of those, then there should be no
   * possible dependency on instance state in the module's bindings.
   *
   * <p>Alternatively, if the module is a Kotlin Object then the binding methods are considered
   * {@code static}, requiring no module instance.
   */
  private boolean requiresModuleInstance(DaggerElements elements) {

    Set<ExecutableElement> methods = elements.getLocalAndInheritedMethods(typeElement());
    return methods.stream()
        .filter(this::isBindingMethod)
        .map(ExecutableElement::getModifiers)
        .anyMatch(modifiers -> !modifiers.contains(ABSTRACT) && !modifiers.contains(STATIC));
  }

  private boolean isBindingMethod(ExecutableElement method) {
    // TODO(cgdecker): At the very least, we should have utility methods to consolidate this stuff
    // in one place; listing individual annotations all over the place is brittle.
    return isAnyAnnotationPresent(
        method,
        TypeNames.PROVIDES,
        // TODO(ronshapiro): it would be cool to have internal meta-annotations that could describe
        // these, like @AbstractBindingMethod
        TypeNames.BINDS,
        TypeNames.MULTIBINDS,
        TypeNames.BINDS_OPTIONAL_OF);
  }

  /** The key for this requirement, if one is available. */
  public Optional<Key> key() {
    return key;
  }

  /** Returns the name for this requirement that could be used as a variable. */
  public String variableName() {
    return variableName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ComponentRequirement that = (ComponentRequirement) o;
    return kind == that.kind
        && wrappedType.equals(that.wrappedType)
        && overrideNullPolicy.equals(that.overrideNullPolicy)
        && key.equals(that.key)
        && variableName.equals(that.variableName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, wrappedType, overrideNullPolicy, key, variableName);
  }

  public static ComponentRequirement forDependency(TypeMirror type) {
    return new ComponentRequirement(
        Kind.DEPENDENCY,
        MoreTypes.equivalence().wrap(checkNotNull(type)),
        Optional.empty(),
        Optional.empty(),
        simpleVariableName(MoreTypes.asTypeElement(type)));
  }

  public static ComponentRequirement forModule(TypeMirror type) {
    return new ComponentRequirement(
        Kind.MODULE,
        MoreTypes.equivalence().wrap(checkNotNull(type)),
        Optional.empty(),
        Optional.empty(),
        simpleVariableName(MoreTypes.asTypeElement(type)));
  }

  static ComponentRequirement forBoundInstance(Key key, boolean nullable, String variableName) {
    return new ComponentRequirement(
        Kind.BOUND_INSTANCE,
        MoreTypes.equivalence().wrap(key.type()),
        nullable ? Optional.of(NullPolicy.ALLOW) : Optional.empty(),
        Optional.of(key),
        variableName);
  }

  public static ComponentRequirement forBoundInstance(ContributionBinding binding) {
    checkArgument(binding.kind().equals(BindingKind.BOUND_INSTANCE));
    return forBoundInstance(
        binding.key(),
        binding.nullableType().isPresent(),
        binding.bindingElement().get().getSimpleName().toString());
  }

  /**
   * Returns true if and only if a component can instantiate new instances (typically of a module)
   * rather than requiring that they be passed.
   */
  // TODO(bcorso): Should this method throw if its called knowing that an instance is not needed?
  public static boolean componentCanMakeNewInstances(
      TypeElement typeElement) {
    switch (typeElement.getKind()) {
      case CLASS:
        break;
      case ENUM:
      case ANNOTATION_TYPE:
      case INTERFACE:
        return false;
      default:
        throw new AssertionError("TypeElement cannot have kind: " + typeElement.getKind());
    }

    if (typeElement.getModifiers().contains(ABSTRACT)) {
      return false;
    }

    if (requiresEnclosingInstance(typeElement)) {
      return false;
    }

    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind().equals(CONSTRUCTOR)
          && MoreElements.asExecutable(enclosed).getParameters().isEmpty()
          && !enclosed.getModifiers().contains(PRIVATE)) {
        return true;
      }
    }

    // TODO(gak): still need checks for visibility

    return false;
  }

  private static boolean requiresEnclosingInstance(TypeElement typeElement) {
    switch (typeElement.getNestingKind()) {
      case TOP_LEVEL:
        return false;
      case MEMBER:
        return !typeElement.getModifiers().contains(STATIC);
      case ANONYMOUS:
      case LOCAL:
        return true;
    }
    throw new AssertionError(
        "TypeElement cannot have nesting kind: " + typeElement.getNestingKind());
  }
}
