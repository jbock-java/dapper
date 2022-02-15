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

import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.xprocessing.XConverters.toXProcessing;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.util.ElementFilter.methodsIn;

import dagger.Binds;
import dagger.Module;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.spi.model.Key;
import io.jbock.common.graph.Traverser;
import io.jbock.javapoet.TypeName;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Contains metadata that describes a module. */
public final class ModuleDescriptor {

  private final XTypeElement moduleElement;
  private final Set<ContributionBinding> bindings;
  private final Set<SubcomponentDeclaration> subcomponentDeclarations;
  private final Set<DelegateDeclaration> delegateDeclarations;
  private final ModuleKind kind;

  ModuleDescriptor(
      XTypeElement moduleElement,
      Set<ContributionBinding> bindings,
      Set<SubcomponentDeclaration> subcomponentDeclarations,
      Set<DelegateDeclaration> delegateDeclarations,
      ModuleKind kind) {
    this.moduleElement = requireNonNull(moduleElement);
    this.bindings = requireNonNull(bindings);
    this.subcomponentDeclarations = requireNonNull(subcomponentDeclarations);
    this.delegateDeclarations = requireNonNull(delegateDeclarations);
    this.kind = requireNonNull(kind);
  }

  public XTypeElement moduleElement() {
    return moduleElement;
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
        && bindings.equals(that.bindings)
        && subcomponentDeclarations.equals(that.subcomponentDeclarations)
        && delegateDeclarations.equals(that.delegateDeclarations)
        && kind == that.kind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(moduleElement,
        bindings,
        subcomponentDeclarations,
        delegateDeclarations,
        kind);
  }

  /** A {@link ModuleDescriptor} factory. */
  @Singleton
  public static final class Factory implements ClearableCache {
    private final XProcessingEnv processingEnv;
    private final DaggerElements elements;
    private final BindingFactory bindingFactory;
    private final DelegateDeclaration.Factory bindingDelegateDeclarationFactory;
    private final SubcomponentDeclaration.Factory subcomponentDeclarationFactory;
    private final Map<XTypeElement, ModuleDescriptor> cache = new HashMap<>();

    @Inject
    Factory(
        XProcessingEnv processingEnv,
        DaggerElements elements,
        BindingFactory bindingFactory,
        DelegateDeclaration.Factory bindingDelegateDeclarationFactory,
        SubcomponentDeclaration.Factory subcomponentDeclarationFactory) {
      this.processingEnv = processingEnv;
      this.elements = elements;
      this.bindingFactory = bindingFactory;
      this.bindingDelegateDeclarationFactory = bindingDelegateDeclarationFactory;
      this.subcomponentDeclarationFactory = subcomponentDeclarationFactory;
    }

    public ModuleDescriptor create(XTypeElement moduleElement) {
      return reentrantComputeIfAbsent(cache, moduleElement, this::createUncached);
    }

    public ModuleDescriptor createUncached(XTypeElement moduleElement) {
      Set<ContributionBinding> bindings = new LinkedHashSet<>();
      Set<DelegateDeclaration> delegates = new LinkedHashSet<>();

      methodsIn(elements.getAllMembers(toJavac(moduleElement))).stream()
          .map(method -> toXProcessing(method, processingEnv))
          .filter(XElement::isMethod)
          .map(XElements::asMethod)
          .forEach(
              moduleMethod -> {
                if (moduleMethod.hasAnnotation(TypeNames.PROVIDES)) {
                  bindings.add(bindingFactory.providesMethodBinding(moduleMethod, moduleElement));
                }
                if (moduleMethod.hasAnnotation(TypeNames.BINDS)) {
                  delegates.add(
                      bindingDelegateDeclarationFactory.create(moduleMethod, moduleElement));
                }
              });

      return new ModuleDescriptor(
          moduleElement,
          bindings,
          subcomponentDeclarationFactory.forModule(moduleElement),
          delegates,
          ModuleKind.forAnnotatedElement(moduleElement).orElseThrow());
    }

    /** Returns all the modules transitively included by given modules, including the arguments. */
    Set<ModuleDescriptor> transitiveModules(Set<XTypeElement> modules) {
      // Traverse as a graph to automatically handle modules with cyclic includes.
      Set<ModuleDescriptor> result = new LinkedHashSet<>();
      Traverser.forGraph(
              (ModuleDescriptor module) -> includedModules(module).stream().map(this::create).collect(Collectors.toList()))
          .depthFirstPreOrder(modules.stream().map(this::create).collect(Collectors.toList()))
          .forEach(result::add);
      return result;
    }


    private Set<XTypeElement> includedModules(ModuleDescriptor moduleDescriptor) {
      return new LinkedHashSet<>(
          collectIncludedModules(new LinkedHashSet<>(), moduleDescriptor.moduleElement()));
    }

    private Set<XTypeElement> collectIncludedModules(
        Set<XTypeElement> includedModules, XTypeElement moduleElement) {

      XType superclass = moduleElement.getSuperType();

      if (superclass != null) {
        Preconditions.checkState(isDeclared(superclass));
        if (!TypeName.OBJECT.equals(superclass.getTypeName())) {
          collectIncludedModules(includedModules, superclass.getTypeElement());
        }
      }

      moduleAnnotation(moduleElement)
          .ifPresent(
              moduleAnnotation ->
                  includedModules.addAll(moduleAnnotation.includes()));
      return includedModules;
    }

    @Override
    public void clearCache() {
      cache.clear();
    }
  }
}
