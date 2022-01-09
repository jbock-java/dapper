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

import static dagger.internal.codegen.base.Scopes.getReadableSource;
import static dagger.internal.codegen.base.Scopes.uniqueScopeOf;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.ModuleDescriptor;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.model.Scope;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/** Validates the relationships between parent components and subcomponents. */
final class ComponentHierarchyValidator {
  private final CompilerOptions compilerOptions;

  @Inject
  ComponentHierarchyValidator(CompilerOptions compilerOptions) {
    this.compilerOptions = compilerOptions;
  }

  ValidationReport<TypeElement> validate(ComponentDescriptor componentDescriptor) {
    ValidationReport.Builder<TypeElement> report =
        ValidationReport.about(componentDescriptor.typeElement());
    validateSubcomponentMethods(
        report,
        componentDescriptor,
        Util.toMap(componentDescriptor.moduleTypes(), module -> componentDescriptor.typeElement()));
    validateRepeatedScopedDeclarations(report, componentDescriptor, new LinkedHashMap<>());

    if (compilerOptions.scopeCycleValidationType().diagnosticKind().isPresent()) {
      validateScopeHierarchy(
          report, componentDescriptor, LinkedHashMultimap.<ComponentDescriptor, Scope>create());
    }
    return report.build();
  }

  private void validateSubcomponentMethods(
      ValidationReport.Builder<?> report,
      ComponentDescriptor componentDescriptor,
      Map<TypeElement, TypeElement> existingModuleToOwners) {
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

              Set<TypeElement> difference = Util.difference(childComponent.moduleTypes(), existingModuleToOwners.keySet());
              Map<TypeElement, TypeElement> newExistingModuleToOwners = new LinkedHashMap<>(Math.max(16, (int) (1.5 * (existingModuleToOwners.size() + difference.size()))));
              newExistingModuleToOwners.putAll(existingModuleToOwners);
              difference.forEach(module ->
                  newExistingModuleToOwners.put(childComponent.typeElement(), module));
              validateSubcomponentMethods(
                  report,
                  childComponent,
                  newExistingModuleToOwners);
            });
  }

  private void validateFactoryMethodParameters(
      ValidationReport.Builder<?> report,
      ComponentMethodDescriptor subcomponentMethodDescriptor,
      Map<TypeElement, TypeElement> existingModuleToOwners) {
    for (VariableElement factoryMethodParameter :
        subcomponentMethodDescriptor.methodElement().getParameters()) {
      TypeElement moduleType = MoreTypes.asTypeElement(factoryMethodParameter.asType());
      TypeElement originatingComponent = existingModuleToOwners.get(moduleType);
      if (originatingComponent != null) {
        /* Factory method tries to pass a module that is already present in the parent.
         * This is an error. */
        report.addError(
            String.format(
                "%s is present in %s. A subcomponent cannot use an instance of a "
                    + "module that differs from its parent.",
                moduleType.getSimpleName(), originatingComponent.getQualifiedName()),
            factoryMethodParameter);
      }
    }
  }

  /**
   * Checks that components do not have any scopes that are also applied on any of their ancestors.
   */
  private void validateScopeHierarchy(
      ValidationReport.Builder<TypeElement> report,
      ComponentDescriptor subject,
      SetMultimap<ComponentDescriptor, Scope> scopesByComponent) {
    scopesByComponent.putAll(subject, subject.scopes());

    for (ComponentDescriptor childComponent : subject.childComponents()) {
      validateScopeHierarchy(report, childComponent, scopesByComponent);
    }

    scopesByComponent.removeAll(subject);

    Predicate<Scope> subjectScopes = subject.scopes()::contains;
    SetMultimap<ComponentDescriptor, Scope> overlappingScopes =
        Multimaps.filterValues(scopesByComponent, subjectScopes::test);
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

  private void validateRepeatedScopedDeclarations(
      ValidationReport.Builder<TypeElement> report,
      ComponentDescriptor component,
      // TODO(ronshapiro): optimize ModuleDescriptor.hashCode()/equals. Otherwise this could be
      // quite costly
      Map<ComponentDescriptor, Set<ModuleDescriptor>> modulesWithScopes) {
    Set<ModuleDescriptor> modules =
        component.modules().stream().filter(this::hasScopedDeclarations).collect(toImmutableSet());
    modules.forEach(module -> modulesWithScopes.merge(component, new LinkedHashSet<>(Set.of(module)), Util::mutableUnion));
    for (ComponentDescriptor childComponent : component.childComponents()) {
      validateRepeatedScopedDeclarations(report, childComponent, modulesWithScopes);
    }
    modulesWithScopes.remove(component);

    Map<ComponentDescriptor, Set<ModuleDescriptor>> repeatedModules =
        Util.filterValues(modulesWithScopes, modules::contains);
    if (repeatedModules.isEmpty()) {
      return;
    }

    report.addError(
        repeatedModulesWithScopeError(component, repeatedModules));
  }

  private boolean hasScopedDeclarations(ModuleDescriptor module) {
    return !moduleScopes(module).isEmpty();
  }

  private String repeatedModulesWithScopeError(
      ComponentDescriptor component,
      Map<ComponentDescriptor, Set<ModuleDescriptor>> repeatedModules) {
    StringBuilder error =
        new StringBuilder()
            .append(component.typeElement().getQualifiedName())
            .append(" repeats modules with scoped bindings or declarations:");

    repeatedModules
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
                    .append(moduleScopes(conflictingModule).stream().map(Scope::toString).collect(Collectors.joining(", ")));
              }
            });
    return error.toString();
  }

  private Set<Scope> moduleScopes(ModuleDescriptor module) {
    return module.allBindingDeclarations().stream()
        .map(declaration -> uniqueScopeOf(declaration.bindingElement().orElseThrow()))
        .filter(scope -> scope.isPresent() && !scope.orElseThrow().isReusable())
        .map(Optional::orElseThrow)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
