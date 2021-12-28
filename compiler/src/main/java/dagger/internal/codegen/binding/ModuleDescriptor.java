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
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.transform;
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
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Traverser;
import com.squareup.javapoet.ClassName;
import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.model.Key;
import dagger.multibindings.Multibinds;
import dagger.producers.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** Contains metadata that describes a module. */
public final class ModuleDescriptor {

  private final TypeElement moduleElement;
  private final ImmutableSet<TypeElement> includedModules;
  private final ImmutableSet<ContributionBinding> bindings;
  private final ImmutableSet<MultibindingDeclaration> multibindingDeclarations;
  private final ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations;
  private final ImmutableSet<DelegateDeclaration> delegateDeclarations;
  private final ImmutableSet<OptionalBindingDeclaration> optionalDeclarations;
  private final ModuleKind kind;

  ModuleDescriptor(
      TypeElement moduleElement,
      ImmutableSet<TypeElement> includedModules,
      ImmutableSet<ContributionBinding> bindings,
      ImmutableSet<MultibindingDeclaration> multibindingDeclarations,
      ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations,
      ImmutableSet<DelegateDeclaration> delegateDeclarations,
      ImmutableSet<OptionalBindingDeclaration> optionalDeclarations,
      ModuleKind kind) {
    this.moduleElement = requireNonNull(moduleElement);
    this.includedModules = requireNonNull(includedModules);
    this.bindings = requireNonNull(bindings);
    this.multibindingDeclarations = requireNonNull(multibindingDeclarations);
    this.subcomponentDeclarations = requireNonNull(subcomponentDeclarations);
    this.delegateDeclarations = requireNonNull(delegateDeclarations);
    this.optionalDeclarations = requireNonNull(optionalDeclarations);
    this.kind = requireNonNull(kind);
  }

  public TypeElement moduleElement() {
    return moduleElement;
  }

  ImmutableSet<TypeElement> includedModules() {
    return includedModules;
  }

  public ImmutableSet<ContributionBinding> bindings() {
    return bindings;
  }

  /** The multibinding declarations contained in this module. */
  ImmutableSet<MultibindingDeclaration> multibindingDeclarations() {
    return multibindingDeclarations;
  }

  /** The {@link Module#subcomponents() subcomponent declarations} contained in this module. */
  ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations() {
    return subcomponentDeclarations;
  }

  /** The {@link Binds} method declarations that define delegate bindings. */
  ImmutableSet<DelegateDeclaration> delegateDeclarations() {
    return delegateDeclarations;
  }

  /** The {@link BindsOptionalOf} method declarations that define optional bindings. */
  ImmutableSet<OptionalBindingDeclaration> optionalDeclarations() {
    return optionalDeclarations;
  }

  /** The kind of the module. */
  public ModuleKind kind() {
    return kind;
  }

  private final Supplier<ImmutableSet<BindingDeclaration>> allBindingDeclarations = Suppliers.memoize(() ->
      ImmutableSet.<BindingDeclaration>builder()
          .addAll(bindings())
          .addAll(delegateDeclarations())
          .addAll(multibindingDeclarations())
          .addAll(optionalDeclarations())
          .addAll(subcomponentDeclarations())
          .build());

  /** Returns all of the bindings declared in this module. */
  public ImmutableSet<BindingDeclaration> allBindingDeclarations() {
    return allBindingDeclarations.get();
  }

  /** Returns the keys of all bindings declared by this module. */
  ImmutableSet<Key> allBindingKeys() {
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
        && multibindingDeclarations.equals(that.multibindingDeclarations)
        && subcomponentDeclarations.equals(that.subcomponentDeclarations)
        && delegateDeclarations.equals(that.delegateDeclarations)
        && optionalDeclarations.equals(that.optionalDeclarations)
        && kind == that.kind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(moduleElement,
        includedModules,
        bindings,
        multibindingDeclarations,
        subcomponentDeclarations,
        delegateDeclarations,
        optionalDeclarations,
        kind);
  }

  /** A {@link ModuleDescriptor} factory. */
  @Singleton
  public static final class Factory implements ClearableCache {
    private final DaggerElements elements;
    private final BindingFactory bindingFactory;
    private final MultibindingDeclaration.Factory multibindingDeclarationFactory;
    private final DelegateDeclaration.Factory bindingDelegateDeclarationFactory;
    private final SubcomponentDeclaration.Factory subcomponentDeclarationFactory;
    private final OptionalBindingDeclaration.Factory optionalBindingDeclarationFactory;
    private final Map<TypeElement, ModuleDescriptor> cache = new HashMap<>();

    @Inject
    Factory(
        DaggerElements elements,
        BindingFactory bindingFactory,
        MultibindingDeclaration.Factory multibindingDeclarationFactory,
        DelegateDeclaration.Factory bindingDelegateDeclarationFactory,
        SubcomponentDeclaration.Factory subcomponentDeclarationFactory,
        OptionalBindingDeclaration.Factory optionalBindingDeclarationFactory) {
      this.elements = elements;
      this.bindingFactory = bindingFactory;
      this.multibindingDeclarationFactory = multibindingDeclarationFactory;
      this.bindingDelegateDeclarationFactory = bindingDelegateDeclarationFactory;
      this.subcomponentDeclarationFactory = subcomponentDeclarationFactory;
      this.optionalBindingDeclarationFactory = optionalBindingDeclarationFactory;
    }

    public ModuleDescriptor create(TypeElement moduleElement) {
      return reentrantComputeIfAbsent(cache, moduleElement, this::createUncached);
    }

    public ModuleDescriptor createUncached(TypeElement moduleElement) {
      ImmutableSet.Builder<ContributionBinding> bindings = ImmutableSet.builder();
      ImmutableSet.Builder<DelegateDeclaration> delegates = ImmutableSet.builder();
      ImmutableSet.Builder<MultibindingDeclaration> multibindingDeclarations =
          ImmutableSet.builder();
      ImmutableSet.Builder<OptionalBindingDeclaration> optionalDeclarations =
          ImmutableSet.builder();

      for (ExecutableElement moduleMethod : methodsIn(elements.getAllMembers(moduleElement))) {
        if (isAnnotationPresent(moduleMethod, Provides.class)) {
          bindings.add(bindingFactory.providesMethodBinding(moduleMethod, moduleElement));
        }
        if (isAnnotationPresent(moduleMethod, Binds.class)) {
          delegates.add(bindingDelegateDeclarationFactory.create(moduleMethod, moduleElement));
        }
        if (isAnnotationPresent(moduleMethod, Multibinds.class)) {
          multibindingDeclarations.add(
              multibindingDeclarationFactory.forMultibindsMethod(moduleMethod, moduleElement));
        }
        if (isAnnotationPresent(moduleMethod, BindsOptionalOf.class)) {
          optionalDeclarations.add(
              optionalBindingDeclarationFactory.forMethod(moduleMethod, moduleElement));
        }
      }

      return new ModuleDescriptor(
          moduleElement,
          ImmutableSet.copyOf(collectIncludedModules(new LinkedHashSet<>(), moduleElement)),
          bindings.build(),
          multibindingDeclarations.build(),
          subcomponentDeclarationFactory.forModule(moduleElement),
          delegates.build(),
          optionalDeclarations.build(),
          ModuleKind.forAnnotatedElement(moduleElement).get());
    }

    /** Returns all the modules transitively included by given modules, including the arguments. */
    ImmutableSet<ModuleDescriptor> transitiveModules(Iterable<TypeElement> modules) {
      return ImmutableSet.copyOf(
          Traverser.forGraph(
                  (ModuleDescriptor module) -> transform(module.includedModules(), this::create))
              .depthFirstPreOrder(transform(modules, this::create)));
    }

    private Set<TypeElement> collectIncludedModules(
        Set<TypeElement> includedModules, TypeElement moduleElement) {
      TypeMirror superclass = moduleElement.getSuperclass();
      if (!superclass.getKind().equals(NONE)) {
        verify(superclass.getKind().equals(DECLARED));
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
    private ImmutableSet<TypeElement> implicitlyIncludedModules(TypeElement moduleElement) {
      TypeElement contributesAndroidInjector =
          elements.getTypeElement("dagger.android.ContributesAndroidInjector");
      if (contributesAndroidInjector == null) {
        return ImmutableSet.of();
      }
      return methodsIn(moduleElement.getEnclosedElements()).stream()
          .filter(method -> isAnnotationPresent(method, contributesAndroidInjector.asType()))
          .map(method -> elements.checkTypePresent(implicitlyIncludedModuleName(method)))
          .collect(toImmutableSet());
    }

    private String implicitlyIncludedModuleName(ExecutableElement method) {
      return getPackage(method).getQualifiedName()
          + "."
          + classFileName(ClassName.get(MoreElements.asType(method.getEnclosingElement())))
          + "_"
          + LOWER_CAMEL.to(UPPER_CAMEL, method.getSimpleName().toString());
    }

    @Override
    public void clearCache() {
      cache.clear();
    }
  }
}
