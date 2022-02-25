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

package dagger.internal.codegen.validation;

import static dagger.internal.codegen.base.Functions.constant;
import static dagger.internal.codegen.base.Predicates.and;
import static dagger.internal.codegen.base.Predicates.in;
import static dagger.internal.codegen.base.Predicates.not;
import static dagger.internal.codegen.base.Scopes.getReadableSource;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import dagger.internal.codegen.base.Joiner;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.binding.ModuleDescriptor;
import dagger.internal.codegen.collect.ImmutableMap;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.collect.ImmutableSetMultimap;
import dagger.internal.codegen.collect.Iterables;
import dagger.internal.codegen.collect.LinkedHashMultimap;
import dagger.internal.codegen.collect.Maps;
import dagger.internal.codegen.collect.Multimaps;
import dagger.internal.codegen.collect.SetMultimap;
import dagger.internal.codegen.collect.Sets;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.xprocessing.XExecutableParameterElement;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.spi.model.Scope;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.Formatter;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** Validates the relationships between parent components and subcomponents. */
final class ComponentHierarchyValidator {
  private static final Joiner COMMA_SEPARATED_JOINER = Joiner.on(", ");

  private final CompilerOptions compilerOptions;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  ComponentHierarchyValidator(
      CompilerOptions compilerOptions, InjectionAnnotations injectionAnnotations) {
    this.compilerOptions = compilerOptions;
    this.injectionAnnotations = injectionAnnotations;
  }

  ValidationReport validate(ComponentDescriptor componentDescriptor) {
    ValidationReport.Builder report = ValidationReport.about(componentDescriptor.typeElement());
    validateSubcomponentMethods(
        report,
        componentDescriptor,
        Maps.toMap(componentDescriptor.moduleTypes(), constant(componentDescriptor.typeElement())));
    validateRepeatedScopedDeclarations(report, componentDescriptor, LinkedHashMultimap.create());

    if (compilerOptions.scopeCycleValidationType().diagnosticKind().isPresent()) {
      validateScopeHierarchy(
          report, componentDescriptor, LinkedHashMultimap.<ComponentDescriptor, Scope>create());
    }
    validateProductionModuleUniqueness(report, componentDescriptor, LinkedHashMultimap.create());
    return report.build();
  }

  private void validateSubcomponentMethods(
      ValidationReport.Builder report,
      ComponentDescriptor componentDescriptor,
      ImmutableMap<XTypeElement, XTypeElement> existingModuleToOwners) {
    componentDescriptor
        .childComponentsDeclaredByFactoryMethods()
        .forEach(
            (method, childComponent) -> {
              if (childComponent.hasCreator()) {
                report.addError(
                    "Components may not have factory methods for subcomponents that define a "
                        + "builder.",
                    method.methodElement());
              } else {
                validateFactoryMethodParameters(report, method, existingModuleToOwners);
              }

              validateSubcomponentMethods(
                  report,
                  childComponent,
                  new ImmutableMap.Builder<XTypeElement, XTypeElement>()
                      .putAll(existingModuleToOwners)
                      .putAll(
                          Maps.toMap(
                              Sets.difference(
                                  childComponent.moduleTypes(), existingModuleToOwners.keySet()),
                              constant(childComponent.typeElement())))
                      .build());
            });
  }

  private void validateFactoryMethodParameters(
      ValidationReport.Builder report,
      ComponentMethodDescriptor subcomponentMethodDescriptor,
      ImmutableMap<XTypeElement, XTypeElement> existingModuleToOwners) {
    for (XExecutableParameterElement factoryMethodParameter :
        subcomponentMethodDescriptor.methodElement().getParameters()) {
      XTypeElement moduleType = factoryMethodParameter.getType().getTypeElement();
      if (existingModuleToOwners.containsKey(moduleType)) {
        /* Factory method tries to pass a module that is already present in the parent.
         * This is an error. */
        report.addError(
            String.format(
                "%s is present in %s. A subcomponent cannot use an instance of a "
                    + "module that differs from its parent.",
                getSimpleName(moduleType),
                existingModuleToOwners.get(moduleType).getQualifiedName()),
            factoryMethodParameter);
      }
    }
  }

  /**
   * Checks that components do not have any scopes that are also applied on any of their ancestors.
   */
  private void validateScopeHierarchy(
      ValidationReport.Builder report,
      ComponentDescriptor subject,
      SetMultimap<ComponentDescriptor, Scope> scopesByComponent) {
    scopesByComponent.putAll(subject, subject.scopes());

    for (ComponentDescriptor childComponent : subject.childComponents()) {
      validateScopeHierarchy(report, childComponent, scopesByComponent);
    }

    scopesByComponent.removeAll(subject);

    Predicate<Scope> subjectScopes =
        subject.isProduction()
            // TODO(beder): validate that @ProductionScope is only applied on production components
            ? and(in(subject.scopes()), not(Scope::isProductionScope))
            : in(subject.scopes());
    SetMultimap<ComponentDescriptor, Scope> overlappingScopes =
        Multimaps.filterValues(scopesByComponent, subjectScopes);
    if (!overlappingScopes.isEmpty()) {
      StringBuilder error =
          new StringBuilder()
              .append(subject.typeElement().getQualifiedName())
              .append(" has conflicting scopes:");
      for (Map.Entry<ComponentDescriptor, Scope> entry : overlappingScopes.entries()) {
        Scope scope = entry.getValue();
        error
            .append("\n  ")
            .append(entry.getKey().typeElement().getQualifiedName())
            .append(" also has ")
            .append(getReadableSource(scope));
      }
      report.addItem(
          error.toString(),
          compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
          subject.typeElement());
    }
  }

  private void validateProductionModuleUniqueness(
      ValidationReport.Builder report,
      ComponentDescriptor componentDescriptor,
      SetMultimap<ComponentDescriptor, ModuleDescriptor> producerModulesByComponent) {
    ImmutableSet<ModuleDescriptor> producerModules = ImmutableSet.of();

    producerModulesByComponent.putAll(componentDescriptor, producerModules);
    for (ComponentDescriptor childComponent : componentDescriptor.childComponents()) {
      validateProductionModuleUniqueness(report, childComponent, producerModulesByComponent);
    }
    producerModulesByComponent.removeAll(componentDescriptor);

    SetMultimap<ComponentDescriptor, ModuleDescriptor> repeatedModules =
        Multimaps.filterValues(producerModulesByComponent, producerModules::contains);
    if (repeatedModules.isEmpty()) {
      return;
    }

    StringBuilder error = new StringBuilder();
    Formatter formatter = new Formatter(error);

    formatter.format("%s repeats @ProducerModules:", componentDescriptor.typeElement());

    for (Map.Entry<ComponentDescriptor, Collection<ModuleDescriptor>> entry :
        repeatedModules.asMap().entrySet()) {
      formatter.format("\n  %s also installs: ", entry.getKey().typeElement());
      COMMA_SEPARATED_JOINER.appendTo(
          error, Iterables.transform(entry.getValue(), m -> m.moduleElement()));
    }

    report.addError(error.toString());
  }

  private void validateRepeatedScopedDeclarations(
      ValidationReport.Builder report,
      ComponentDescriptor component,
      // TODO(ronshapiro): optimize ModuleDescriptor.hashCode()/equals. Otherwise this could be
      // quite costly
      SetMultimap<ComponentDescriptor, ModuleDescriptor> modulesWithScopes) {
    ImmutableSet<ModuleDescriptor> modules =
        component.modules().stream().filter(this::hasScopedDeclarations).collect(toImmutableSet());
    modulesWithScopes.putAll(component, modules);
    for (ComponentDescriptor childComponent : component.childComponents()) {
      validateRepeatedScopedDeclarations(report, childComponent, modulesWithScopes);
    }
    modulesWithScopes.removeAll(component);

    SetMultimap<ComponentDescriptor, ModuleDescriptor> repeatedModules =
        Multimaps.filterValues(modulesWithScopes, modules::contains);
    if (repeatedModules.isEmpty()) {
      return;
    }

    report.addError(
        repeatedModulesWithScopeError(component, ImmutableSetMultimap.copyOf(repeatedModules)));
  }

  private boolean hasScopedDeclarations(ModuleDescriptor module) {
    return !moduleScopes(module).isEmpty();
  }

  private String repeatedModulesWithScopeError(
      ComponentDescriptor component,
      ImmutableSetMultimap<ComponentDescriptor, ModuleDescriptor> repeatedModules) {
    StringBuilder error =
        new StringBuilder()
            .append(component.typeElement().getQualifiedName())
            .append(" repeats modules with scoped bindings or declarations:");

    repeatedModules
        .asMap()
        .forEach(
            (conflictingComponent, conflictingModules) -> {
              error
                  .append("\n  - ")
                  .append(conflictingComponent.typeElement().getQualifiedName())
                  .append(" also includes:");
              for (ModuleDescriptor conflictingModule : conflictingModules) {
                error
                    .append("\n    - ")
                    .append(conflictingModule.moduleElement().getQualifiedName())
                    .append(" with scopes: ")
                    .append(COMMA_SEPARATED_JOINER.join(moduleScopes(conflictingModule)));
              }
            });
    return error.toString();
  }

  private ImmutableSet<Scope> moduleScopes(ModuleDescriptor module) {
    return module.allBindingDeclarations().stream()
        .map(declaration -> injectionAnnotations.getScope(declaration.bindingElement().get()))
        .filter(scope -> scope.isPresent() && !scope.get().isReusable())
        .map(Optional::get)
        .collect(toImmutableSet());
  }
}
