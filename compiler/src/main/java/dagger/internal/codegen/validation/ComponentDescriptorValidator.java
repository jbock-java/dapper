/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static dagger.internal.codegen.base.ComponentAnnotation.rootComponentAnnotation;
import static dagger.internal.codegen.base.DiagnosticFormatting.stripCommonTypePrefixes;
import static dagger.internal.codegen.base.Formatter.INDENT;
import static dagger.internal.codegen.base.Scopes.getReadableSource;
import static dagger.internal.codegen.base.Scopes.scopesOf;
import static dagger.internal.codegen.base.Scopes.singletonScope;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSetMultimap;
import static io.jbock.auto.common.MoreTypes.asDeclared;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static javax.tools.Diagnostic.Kind.ERROR;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.binding.ComponentCreatorDescriptor;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ComponentRequirement.NullPolicy;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.ErrorMessages;
import dagger.internal.codegen.binding.ErrorMessages.ComponentCreatorMessages;
import dagger.internal.codegen.binding.MethodSignatureFormatter;
import dagger.internal.codegen.binding.ModuleDescriptor;
import dagger.internal.codegen.binding.ModuleKind;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.compileroption.ValidationType;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Scope;
import io.jbock.auto.common.Equivalence.Wrapper;
import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import jakarta.inject.Inject;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Reports errors in the component hierarchy.
 *
 * <ul>
 *   <li>Validates scope hierarchy of component dependencies and subcomponents.
 *   <li>Reports errors if there are component dependency cycles.
 *   <li>Reports errors if any abstract modules have non-abstract instance binding methods.
 *   <li>Validates component creator types.
 * </ul>
 */
// TODO(dpb): Combine with ComponentHierarchyValidator.
public final class ComponentDescriptorValidator {

  private final DaggerElements elements;
  private final DaggerTypes types;
  private final CompilerOptions compilerOptions;
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final ComponentHierarchyValidator componentHierarchyValidator;

  @Inject
  ComponentDescriptorValidator(
      DaggerElements elements,
      DaggerTypes types,
      CompilerOptions compilerOptions,
      MethodSignatureFormatter methodSignatureFormatter,
      ComponentHierarchyValidator componentHierarchyValidator) {
    this.elements = elements;
    this.types = types;
    this.compilerOptions = compilerOptions;
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.componentHierarchyValidator = componentHierarchyValidator;
  }

  public ValidationReport<TypeElement> validate(ComponentDescriptor component) {
    ComponentValidation validation = new ComponentValidation(component);
    validation.visitComponent(component);
    validation.report(component).addSubreport(componentHierarchyValidator.validate(component));
    return validation.buildReport();
  }

  private final class ComponentValidation {
    final ComponentDescriptor rootComponent;
    final Map<ComponentDescriptor, ValidationReport.Builder<TypeElement>> reports =
        new LinkedHashMap<>();

    ComponentValidation(ComponentDescriptor rootComponent) {
      this.rootComponent = requireNonNull(rootComponent);
    }

    /** Returns a report that contains all validation messages found during traversal. */
    ValidationReport<TypeElement> buildReport() {
      ValidationReport.Builder<TypeElement> report =
          ValidationReport.about(rootComponent.typeElement());
      reports.values().forEach(subreport -> report.addSubreport(subreport.build()));
      return report.build();
    }

    /** Returns the report builder for a (sub)component. */
    private ValidationReport.Builder<TypeElement> report(ComponentDescriptor component) {
      return reentrantComputeIfAbsent(
          reports, component, descriptor -> ValidationReport.about(descriptor.typeElement()));
    }

    private void reportComponentItem(
        Diagnostic.Kind kind, ComponentDescriptor component, String message) {
      report(component)
          .addItem(message, kind, component.typeElement(), component.annotation().annotation());
    }

    private void reportComponentError(ComponentDescriptor component, String error) {
      reportComponentItem(ERROR, component, error);
    }

    void visitComponent(ComponentDescriptor component) {
      validateDependencyScopes(component);
      validateComponentDependencyHierarchy(component);
      validateModules(component);
      validateCreators(component);
      component.childComponents().forEach(this::visitComponent);
    }

    /** Validates that component dependencies do not form a cycle. */
    private void validateComponentDependencyHierarchy(ComponentDescriptor component) {
      validateComponentDependencyHierarchy(component, component.typeElement(), new ArrayDeque<>());
    }

    /** Recursive method to validate that component dependencies do not form a cycle. */
    private void validateComponentDependencyHierarchy(
        ComponentDescriptor component, TypeElement dependency, Deque<TypeElement> dependencyStack) {
      if (dependencyStack.contains(dependency)) {
        // Current component has already appeared in the component chain.
        StringBuilder message = new StringBuilder();
        message.append(component.typeElement().getQualifiedName());
        message.append(" contains a cycle in its component dependencies:\n");
        dependencyStack.push(dependency);
        appendIndentedComponentsList(message, dependencyStack);
        dependencyStack.pop();
        reportComponentItem(
            compilerOptions.scopeCycleValidationType().diagnosticKind().orElseThrow(),
            component,
            message.toString());
      } else if (compilerOptions.validateTransitiveComponentDependencies()
          // Always validate direct component dependencies referenced by this component regardless
          // of the flag value
          || dependencyStack.isEmpty()) {
        rootComponentAnnotation(dependency)
            .ifPresent(
                componentAnnotation -> {
                  dependencyStack.push(dependency);

                  for (TypeElement nextDependency : componentAnnotation.dependencies()) {
                    validateComponentDependencyHierarchy(
                        component, nextDependency, dependencyStack);
                  }

                  dependencyStack.pop();
                });
      }
    }

    /**
     * Validates that among the dependencies there are no cycles within the scoping chain, and that
     * singleton components have no scoped dependencies.
     */
    private void validateDependencyScopes(ComponentDescriptor component) {
      Set<Scope> scopes = component.scopes();
      Set<TypeElement> scopedDependencies =
          scopedTypesIn(
              component
                  .dependencies()
                  .stream()
                  .map(ComponentRequirement::typeElement)
                  .collect(toImmutableSet()));
      if (!scopes.isEmpty()) {
        Scope singletonScope = singletonScope(elements);
        // Dagger 1.x scope compatibility requires this be suppress-able.
        if (compilerOptions.scopeCycleValidationType().diagnosticKind().isPresent()
            && scopes.contains(singletonScope)) {
          // Singleton is a special-case representing the longest lifetime, and therefore
          // @Singleton components may not depend on scoped components
          if (!scopedDependencies.isEmpty()) {
            StringBuilder message =
                new StringBuilder(
                    "This @Singleton component cannot depend on scoped components:\n");
            appendIndentedComponentsList(message, scopedDependencies);
            reportComponentItem(
                compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
                component,
                message.toString());
          }
        } else {
          // Dagger 1.x scope compatibility requires this be suppress-able.
          if (!compilerOptions.scopeCycleValidationType().equals(ValidationType.NONE)) {
            validateDependencyScopeHierarchy(
                component, component.typeElement(), new ArrayDeque<>(), new ArrayDeque<>());
          }
        }
      } else {
        // Scopeless components may not depend on scoped components.
        if (!scopedDependencies.isEmpty()) {
          StringBuilder message =
              new StringBuilder(component.typeElement().getQualifiedName())
                  .append(" (unscoped) cannot depend on scoped components:\n");
          appendIndentedComponentsList(message, scopedDependencies);
          reportComponentError(component, message.toString());
        }
      }
    }

    private void validateModules(ComponentDescriptor component) {
      for (ModuleDescriptor module : component.modules()) {
        if (module.moduleElement().getModifiers().contains(Modifier.ABSTRACT)) {
          for (ContributionBinding binding : module.bindings()) {
            if (binding.requiresModuleInstance()) {
              report(component).addError(abstractModuleHasInstanceBindingMethodsError(module));
              break;
            }
          }
        }
      }
    }

    private String abstractModuleHasInstanceBindingMethodsError(ModuleDescriptor module) {
      Preconditions.checkArgument(module.kind() == ModuleKind.MODULE, module.kind());
      return String.format(
          "%s is abstract and has instance @Provides methods. Consider making the methods static or "
              + "including a non-abstract subclass of the module instead.",
          module.moduleElement());
    }

    private void validateCreators(ComponentDescriptor component) {
      if (component.creatorDescriptor().isEmpty()) {
        // If no builder, nothing to validate.
        return;
      }

      ComponentCreatorDescriptor creator = component.creatorDescriptor().get();
      ComponentCreatorMessages messages = ErrorMessages.creatorMessagesFor(creator.annotation());

      // Requirements for modules and dependencies that the creator can set
      Set<ComponentRequirement> creatorModuleAndDependencyRequirements =
          creator.moduleAndDependencyRequirements();
      // Modules and dependencies the component requires
      Set<ComponentRequirement> componentModuleAndDependencyRequirements =
          component.dependenciesAndConcreteModules();

      // Requirements that the creator can set that don't match any requirements that the component
      // actually has.
      Set<ComponentRequirement> inapplicableRequirementsOnCreator =
          Util.difference(
              creatorModuleAndDependencyRequirements, componentModuleAndDependencyRequirements);

      DeclaredType container = asDeclared(creator.typeElement().asType());
      if (!inapplicableRequirementsOnCreator.isEmpty()) {
        Collection<Element> excessElements =
            creator.unvalidatedRequirementElements().entrySet().stream()
                .filter(e -> inapplicableRequirementsOnCreator.contains(e.getKey()))
                .map(Entry::getValue)
                .flatMap(Set::stream)
                .collect(Collectors.toList());
        String formatted =
            excessElements.stream()
                .map(element -> formatElement(element, container))
                .collect(joining(", ", "[", "]"));
        report(component)
            .addError(String.format(messages.extraSetters(), formatted), creator.typeElement());
      }

      // Component requirements that the creator must be able to set
      Set<ComponentRequirement> mustBePassed =
          componentModuleAndDependencyRequirements.stream()
              .filter(
                  input -> input.nullPolicy(elements).equals(NullPolicy.THROW))
              .collect(Collectors.toCollection(LinkedHashSet::new));
      // Component requirements that the creator must be able to set, but can't
      Set<ComponentRequirement> missingRequirements =
          Util.difference(mustBePassed, creatorModuleAndDependencyRequirements);

      if (!missingRequirements.isEmpty()) {
        report(component)
            .addError(
                String.format(
                    messages.missingSetters(),
                    missingRequirements.stream().map(ComponentRequirement::type).collect(toList())),
                creator.typeElement());
      }

      // Validate that declared creator requirements (modules, dependencies) have unique types.
      Map<Wrapper<TypeMirror>, Set<Element>> declaredRequirementsByType =
          creator.unvalidatedRequirementElements().entrySet().stream()
              .filter(entry -> creatorModuleAndDependencyRequirements.contains(entry.getKey()))
              .flatMap(entry -> entry.getValue().stream().map(value -> new SimpleImmutableEntry<>(entry.getKey(), value)))
              .collect(
                  toImmutableSetMultimap(entry -> entry.getKey().wrappedType(), Entry::getValue));
      declaredRequirementsByType
          .forEach(
              (typeWrapper, elementsForType) -> {
                if (elementsForType.size() > 1) {
                  TypeMirror type = typeWrapper.get();
                  // TODO(cgdecker): Attach this error message to the factory method rather than
                  // the component type if the elements are factory method parameters AND the
                  // factory method is defined by the factory type itself and not by a supertype.
                  report(component)
                      .addError(
                          String.format(
                              messages.multipleSettersForModuleOrDependencyType(),
                              type,
                              elementsForType.stream().map(
                                  element -> formatElement(element, container)).collect(toList())),
                          creator.typeElement());
                }
              });

      // TODO(cgdecker): Duplicate binding validation should handle the case of multiple elements
      // that set the same bound-instance Key, but validating that here would make it fail faster
      // for subcomponents.
    }

    private String formatElement(Element element, DeclaredType container) {
      // TODO(cgdecker): Extract some or all of this to another class?
      // But note that it does different formatting for parameters than
      // DaggerElements.elementToString(Element).
      switch (element.getKind()) {
        case METHOD:
          return methodSignatureFormatter.format(
              MoreElements.asExecutable(element), Optional.of(container));
        case PARAMETER:
          return formatParameter(MoreElements.asVariable(element), container);
        default:
          // This method shouldn't be called with any other type of element.
          throw new AssertionError();
      }
    }

    private String formatParameter(VariableElement parameter, DeclaredType container) {
      // TODO(cgdecker): Possibly leave the type (and annotations?) off of the parameters here and
      // just use their names, since the type will be redundant in the context of the error message.
      StringJoiner joiner = new StringJoiner(" ");
      parameter.getAnnotationMirrors().stream().map(Object::toString).forEach(joiner::add);
      TypeMirror parameterType = resolveParameterType(parameter, container);
      return joiner
          .add(stripCommonTypePrefixes(parameterType.toString()))
          .add(parameter.getSimpleName())
          .toString();
    }

    private TypeMirror resolveParameterType(VariableElement parameter, DeclaredType container) {
      ExecutableElement method =
          MoreElements.asExecutable(parameter.getEnclosingElement());
      int parameterIndex = method.getParameters().indexOf(parameter);

      ExecutableType methodType = MoreTypes.asExecutable(types.asMemberOf(container, method));
      return methodType.getParameterTypes().get(parameterIndex);
    }

    /**
     * Validates that scopes do not participate in a scoping cycle - that is to say, scoped
     * components are in a hierarchical relationship terminating with Singleton.
     *
     * <p>As a side-effect, this means scoped components cannot have a dependency cycle between
     * themselves, since a component's presence within its own dependency path implies a cyclical
     * relationship between scopes. However, cycles in component dependencies are explicitly checked
     * in {@link #validateComponentDependencyHierarchy(ComponentDescriptor)}.
     */
    private void validateDependencyScopeHierarchy(
        ComponentDescriptor component,
        TypeElement dependency,
        Deque<Set<Scope>> scopeStack,
        Deque<TypeElement> scopedDependencyStack) {
      Set<Scope> scopes = scopesOf(dependency);
      if (stackOverlaps(scopeStack, scopes)) {
        scopedDependencyStack.push(dependency);
        // Current scope has already appeared in the component chain.
        StringBuilder message = new StringBuilder();
        message.append(component.typeElement().getQualifiedName());
        message.append(" depends on scoped components in a non-hierarchical scope ordering:\n");
        appendIndentedComponentsList(message, scopedDependencyStack);
        if (compilerOptions.scopeCycleValidationType().diagnosticKind().isPresent()) {
          reportComponentItem(
              compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
              component,
              message.toString());
        }
        scopedDependencyStack.pop();
      } else if (compilerOptions.validateTransitiveComponentDependencies()
          // Always validate direct component dependencies referenced by this component regardless
          // of the flag value
          || scopedDependencyStack.isEmpty()) {
        // TODO(beder): transitively check scopes of production components too.
        rootComponentAnnotation(dependency)
            .ifPresent(
                componentAnnotation -> {
                  Set<TypeElement> scopedDependencies =
                      scopedTypesIn(componentAnnotation.dependencies());
                  if (!scopedDependencies.isEmpty()) {
                    // empty can be ignored (base-case)
                    scopeStack.push(scopes);
                    scopedDependencyStack.push(dependency);
                    for (TypeElement scopedDependency : scopedDependencies) {
                      validateDependencyScopeHierarchy(
                          component,
                          scopedDependency,
                          scopeStack,
                          scopedDependencyStack);
                    }
                    scopedDependencyStack.pop();
                    scopeStack.pop();
                  }
                }); // else: we skip component dependencies which are not components
      }
    }

    private <T> boolean stackOverlaps(Deque<Set<T>> stack, Set<T> set) {
      for (Set<T> entry : stack) {
        if (!Util.intersection(entry, set).isEmpty()) {
          return true;
        }
      }
      return false;
    }

    /** Appends and formats a list of indented component types (with their scope annotations). */
    private void appendIndentedComponentsList(StringBuilder message, Iterable<TypeElement> types) {
      for (TypeElement scopedComponent : types) {
        message.append(INDENT);
        for (Scope scope : scopesOf(scopedComponent)) {
          message.append(getReadableSource(scope)).append(' ');
        }
        message
            .append(stripCommonTypePrefixes(scopedComponent.getQualifiedName().toString()))
            .append('\n');
      }
    }

    /**
     * Returns a set of type elements containing only those found in the input set that have a
     * scoping annotation.
     */
    private Set<TypeElement> scopedTypesIn(Collection<TypeElement> types) {
      return types.stream().filter(type -> !scopesOf(type).isEmpty()).collect(toImmutableSet());
    }
  }
}
