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

import static dagger.internal.codegen.base.CaseFormat.LOWER_CAMEL;
import static dagger.internal.codegen.base.CaseFormat.UPPER_CAMEL;
import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.base.Verify.verify;
import static dagger.internal.codegen.binding.SourceFiles.classFileName;
import static dagger.internal.codegen.collect.Collections2.transform;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.getMethodDescriptor;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.xprocessing.XConverters.toXProcessing;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static io.jbock.auto.common.MoreElements.asExecutable;
import static javax.lang.model.util.ElementFilter.methodsIn;

import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.base.ModuleKind;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.spi.model.Key;
import io.jbock.auto.value.AutoValue;
import io.jbock.auto.value.extension.memoized.Memoized;
import io.jbock.common.graph.Traverser;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.TypeName;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Contains metadata that describes a module. */
@AutoValue
public abstract class ModuleDescriptor {

  public abstract XTypeElement moduleElement();

  public abstract ImmutableSet<ContributionBinding> bindings();

  /** The {@code Module#subcomponents() subcomponent declarations} contained in this module. */
  abstract ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations();

  /** The {@code Binds} method declarations that define delegate bindings. */
  abstract ImmutableSet<DelegateDeclaration> delegateDeclarations();

  /** The kind of the module. */
  public abstract ModuleKind kind();

  /** Returns all of the bindings declared in this module. */
  @Memoized
  public ImmutableSet<BindingDeclaration> allBindingDeclarations() {
    return ImmutableSet.<BindingDeclaration>builder()
        .addAll(bindings())
        .addAll(delegateDeclarations())
        .addAll(subcomponentDeclarations())
        .build();
  }

  /** Returns the keys of all bindings declared by this module. */
  ImmutableSet<Key> allBindingKeys() {
    return allBindingDeclarations().stream().map(BindingDeclaration::key).collect(toImmutableSet());
  }

  /** A {@code ModuleDescriptor} factory. */
  @Singleton
  public static final class Factory implements ClearableCache {
    private final XProcessingEnv processingEnv;
    private final DaggerElements elements;
    private final BindingFactory bindingFactory;
    private final DelegateDeclaration.Factory bindingDelegateDeclarationFactory;
    private final SubcomponentDeclaration.Factory subcomponentDeclarationFactory;
    private final DaggerSuperficialValidation superficialValidation;
    private final Map<XTypeElement, ModuleDescriptor> cache = new HashMap<>();

    @Inject
    Factory(
        XProcessingEnv processingEnv,
        DaggerElements elements,
        BindingFactory bindingFactory,
        DelegateDeclaration.Factory bindingDelegateDeclarationFactory,
        SubcomponentDeclaration.Factory subcomponentDeclarationFactory,
        DaggerSuperficialValidation superficialValidation) {
      this.processingEnv = processingEnv;
      this.elements = elements;
      this.bindingFactory = bindingFactory;
      this.bindingDelegateDeclarationFactory = bindingDelegateDeclarationFactory;
      this.subcomponentDeclarationFactory = subcomponentDeclarationFactory;
      this.superficialValidation = superficialValidation;
    }

    public ModuleDescriptor create(XTypeElement moduleElement) {
      return reentrantComputeIfAbsent(cache, moduleElement, this::createUncached);
    }

    public ModuleDescriptor createUncached(XTypeElement moduleElement) {
      ImmutableSet.Builder<ContributionBinding> bindings = ImmutableSet.builder();
      ImmutableSet.Builder<DelegateDeclaration> delegates = ImmutableSet.builder();

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

      moduleElement.getEnclosedTypeElements().stream()
          .filter(XTypeElement::isCompanionObject)
          .collect(toOptional())
          .ifPresent(companionModule -> collectCompanionModuleBindings(companionModule, bindings));

      return new AutoValue_ModuleDescriptor(
          moduleElement,
          bindings.build(),
          subcomponentDeclarationFactory.forModule(moduleElement),
          delegates.build(),
          ModuleKind.forAnnotatedElement(moduleElement).get());
    }

    private void collectCompanionModuleBindings(
        XTypeElement companionModule, ImmutableSet.Builder<ContributionBinding> bindings) {
      ImmutableSet<String> bindingElementDescriptors =
          bindings.build().stream()
              .map(
                  binding ->
                      getMethodDescriptor(asExecutable(toJavac(binding.bindingElement().get()))))
              .collect(toImmutableSet());

      methodsIn(elements.getAllMembers(toJavac(companionModule))).stream()
          .map(method -> toXProcessing(method, processingEnv))
          .filter(XElement::isMethod)
          .map(XElements::asMethod)
          // Binding methods in companion objects with @JvmStatic are mirrored in the enclosing
          // class, therefore we should ignore it or else it'll be a duplicate binding.
          .filter(method -> !method.hasAnnotation(TypeNames.JVM_STATIC))
          // Fallback strategy for de-duping contributing bindings in the companion module with
          // @JvmStatic by comparing descriptors. Contributing bindings are the only valid bindings
          // a companion module can declare. See: https://youtrack.jetbrains.com/issue/KT-35104
          // TODO(danysantiago): Checks qualifiers too.
          .filter(method -> !bindingElementDescriptors.contains(getMethodDescriptor(method)))
          .forEach(
              method -> {
                if (method.hasAnnotation(TypeNames.PROVIDES)) {
                  bindings.add(bindingFactory.providesMethodBinding(method, companionModule));
                }
              });
    }

    /** Returns all the modules transitively included by given modules, including the arguments. */
    ImmutableSet<ModuleDescriptor> transitiveModules(Collection<XTypeElement> modules) {
      // Traverse as a graph to automatically handle modules with cyclic includes.
      return ImmutableSet.copyOf(
          Traverser.forGraph(
                  (ModuleDescriptor module) -> transform(includedModules(module), this::create))
              .depthFirstPreOrder(transform(modules, this::create)));
    }

    private ImmutableSet<XTypeElement> includedModules(ModuleDescriptor moduleDescriptor) {
      return ImmutableSet.copyOf(
          collectIncludedModules(new LinkedHashSet<>(), moduleDescriptor.moduleElement()));
    }

    private Set<XTypeElement> collectIncludedModules(
        Set<XTypeElement> includedModules, XTypeElement moduleElement) {
      XType superclass = moduleElement.getSuperType();
      if (superclass != null) {
        verify(isDeclared(superclass));
        if (!TypeName.OBJECT.equals(superclass.getTypeName())) {
          collectIncludedModules(includedModules, superclass.getTypeElement());
        }
      }
      moduleAnnotation(moduleElement, superficialValidation)
          .ifPresent(
              moduleAnnotation -> {
                includedModules.addAll(moduleAnnotation.includes());
                includedModules.addAll(implicitlyIncludedModules(moduleElement));
              });
      return includedModules;
    }

    private static final ClassName CONTRIBUTES_ANDROID_INJECTOR =
        ClassName.get("dagger.android", "ContributesAndroidInjector");

    // @ContributesAndroidInjector generates a module that is implicitly included in the enclosing
    // module
    private ImmutableSet<XTypeElement> implicitlyIncludedModules(XTypeElement module) {
      if (processingEnv.findTypeElement(CONTRIBUTES_ANDROID_INJECTOR) == null) {
        return ImmutableSet.of();
      }
      return module.getDeclaredMethods().stream()
          .filter(method -> method.hasAnnotation(CONTRIBUTES_ANDROID_INJECTOR))
          .map(
              method ->
                  DaggerSuperficialValidation.requireTypeElement(
                      processingEnv, implicitlyIncludedModuleName(module, method)))
          .collect(toImmutableSet());
    }

    private ClassName implicitlyIncludedModuleName(XTypeElement module, XMethodElement method) {
      return ClassName.get(
          module.getPackageName(),
          String.format(
              "%s_%s",
              classFileName(module.getClassName()),
              LOWER_CAMEL.to(UPPER_CAMEL, getSimpleName(method))));
    }

    @Override
    public void clearCache() {
      cache.clear();
    }
  }
}
