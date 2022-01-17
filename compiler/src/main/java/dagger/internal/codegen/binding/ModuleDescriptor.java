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

package dagger.internal.codegen.binding;

import static com.google.auto.common.MoreElements.getPackage;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.SourceFiles.classFileName;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.NONE;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.graph.Traverser;
import com.squareup.javapoet.ClassName;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.model.Key;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** Contains metadata that describes a module. */
public final class ModuleDescriptor {

  private final TypeElement moduleElement;
  private final Set<TypeElement> includedModules;
  private final Set<ContributionBinding> bindings;
  private final Set<SubcomponentDeclaration> subcomponentDeclarations;
  private final Set<DelegateDeclaration> delegateDeclarations;
  private final ModuleKind kind;

  ModuleDescriptor(
      TypeElement moduleElement,
      Set<TypeElement> includedModules,
      Set<ContributionBinding> bindings,
      Set<SubcomponentDeclaration> subcomponentDeclarations,
      Set<DelegateDeclaration> delegateDeclarations,
      ModuleKind kind) {
    this.moduleElement = requireNonNull(moduleElement);
    this.includedModules = requireNonNull(includedModules);
    this.bindings = requireNonNull(bindings);
    this.subcomponentDeclarations = requireNonNull(subcomponentDeclarations);
    this.delegateDeclarations = requireNonNull(delegateDeclarations);
    this.kind = requireNonNull(kind);
  }

  public TypeElement moduleElement() {
    return moduleElement;
  }

  Set<TypeElement> includedModules() {
    return includedModules;
  }

  public Set<ContributionBinding> bindings() {
    return bindings;
  }

  /** The {@link Module#subcomponents() subcomponent declarations} contained in this module. */
  Set<SubcomponentDeclaration> subcomponentDeclarations() {
    return subcomponentDeclarations;
  }

  /** The {@link Binds} method declarations that define delegate bindings. */
  Set<DelegateDeclaration> delegateDeclarations() {
    return delegateDeclarations;
  }

  /** The kind of the module. */
  public ModuleKind kind() {
    return kind;
  }

  private final Supplier<Set<BindingDeclaration>> allBindingDeclarations = Suppliers.memoize(() -> {
    Set<BindingDeclaration> result = new LinkedHashSet<>();
    result.addAll(bindings());
    result.addAll(delegateDeclarations());
    result.addAll(subcomponentDeclarations());
    return result;
  });

  /** Returns all of the bindings declared in this module. */
  public Set<BindingDeclaration> allBindingDeclarations() {
    return allBindingDeclarations.get();
  }

  /** Returns the keys of all bindings declared by this module. */
  Set<Key> allBindingKeys() {
    return allBindingDeclarations().stream().map(BindingDeclaration::key).collect(toImmutableSet());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ModuleDescriptor that = (ModuleDescriptor) o;
    return moduleElement.equals(that.moduleElement)
        && includedModules.equals(that.includedModules)
        && bindings.equals(that.bindings)
        && subcomponentDeclarations.equals(that.subcomponentDeclarations)
        && delegateDeclarations.equals(that.delegateDeclarations)
        && kind == that.kind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(moduleElement,
        includedModules,
        bindings,
        subcomponentDeclarations,
        delegateDeclarations,
        kind);
  }

  /** A {@link ModuleDescriptor} factory. */
  @Singleton
  public static final class Factory implements ClearableCache {
    private final DaggerElements elements;
    private final BindingFactory bindingFactory;
    private final DelegateDeclaration.Factory bindingDelegateDeclarationFactory;
    private final SubcomponentDeclaration.Factory subcomponentDeclarationFactory;
    private final Map<TypeElement, ModuleDescriptor> cache = new HashMap<>();

    @Inject
    Factory(
        DaggerElements elements,
        BindingFactory bindingFactory,
        DelegateDeclaration.Factory bindingDelegateDeclarationFactory,
        SubcomponentDeclaration.Factory subcomponentDeclarationFactory) {
      this.elements = elements;
      this.bindingFactory = bindingFactory;
      this.bindingDelegateDeclarationFactory = bindingDelegateDeclarationFactory;
      this.subcomponentDeclarationFactory = subcomponentDeclarationFactory;
    }

    public ModuleDescriptor create(TypeElement moduleElement) {
      return reentrantComputeIfAbsent(cache, moduleElement, this::createUncached);
    }

    public ModuleDescriptor createUncached(TypeElement moduleElement) {
      Set<ContributionBinding> bindings = new LinkedHashSet<>();
      Set<DelegateDeclaration> delegates = new LinkedHashSet<>();

      for (ExecutableElement moduleMethod : methodsIn(elements.getAllMembers(moduleElement))) {
        if (isAnnotationPresent(moduleMethod, Provides.class)) {
          bindings.add(bindingFactory.providesMethodBinding(moduleMethod, moduleElement));
        }
        if (isAnnotationPresent(moduleMethod, Binds.class)) {
          delegates.add(bindingDelegateDeclarationFactory.create(moduleMethod, moduleElement));
        }
      }

      return new ModuleDescriptor(
          moduleElement,
          collectIncludedModules(new LinkedHashSet<>(), moduleElement),
          bindings,
          subcomponentDeclarationFactory.forModule(moduleElement),
          delegates,
          ModuleKind.forAnnotatedElement(moduleElement).orElseThrow());
    }

    /** Returns all the modules transitively included by given modules, including the arguments. */
    Set<ModuleDescriptor> transitiveModules(Set<TypeElement> modules) {
      Set<ModuleDescriptor> result = new LinkedHashSet<>();
      Traverser.forGraph(
              (ModuleDescriptor module) -> module.includedModules().stream().map(this::create).collect(Collectors.toList()))
          .depthFirstPreOrder(modules.stream().map(this::create).collect(Collectors.toList()))
          .forEach(result::add);
      return result;
    }

    private Set<TypeElement> collectIncludedModules(
        Set<TypeElement> includedModules, TypeElement moduleElement) {
      TypeMirror superclass = moduleElement.getSuperclass();
      if (!superclass.getKind().equals(NONE)) {
        Preconditions.checkState(superclass.getKind().equals(DECLARED));
        TypeElement superclassElement = MoreTypes.asTypeElement(superclass);
        if (!superclassElement.getQualifiedName().contentEquals(Object.class.getCanonicalName())) {
          collectIncludedModules(includedModules, superclassElement);
        }
      }
      moduleAnnotation(moduleElement)
          .ifPresent(
              moduleAnnotation -> {
                includedModules.addAll(moduleAnnotation.includes());
                includedModules.addAll(implicitlyIncludedModules(moduleElement));
              });
      return includedModules;
    }

    // @ContributesAndroidInjector generates a module that is implicitly included in the enclosing
    // module
    private Set<TypeElement> implicitlyIncludedModules(TypeElement moduleElement) {
      TypeElement contributesAndroidInjector =
          elements.getTypeElement("dagger.android.ContributesAndroidInjector");
      if (contributesAndroidInjector == null) {
        return Set.of();
      }
      return methodsIn(moduleElement.getEnclosedElements()).stream()
          .filter(method -> isAnnotationPresent(method, contributesAndroidInjector.asType()))
          .map(method -> elements.checkTypePresent(implicitlyIncludedModuleName(method)))
          .collect(toImmutableSet());
    }

    private String implicitlyIncludedModuleName(ExecutableElement method) {
      String name = method.getSimpleName().toString();
      return getPackage(method).getQualifiedName()
          + "."
          + classFileName(ClassName.get(MoreElements.asType(method.getEnclosingElement())))
          + "_"
          + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Override
    public void clearCache() {
      cache.clear();
    }
  }
}
