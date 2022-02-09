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

package dagger.internal.codegen.bindinggraphvalidation;

import static dagger.internal.codegen.base.Formatter.INDENT;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSetMultimap;
import static dagger.model.BindingKind.INJECTION;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static javax.tools.Diagnostic.Kind.ERROR;

import dagger.internal.codegen.base.Formatter;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.binding.BindingDeclaration;
import dagger.internal.codegen.binding.BindingDeclarationFormatter;
import dagger.internal.codegen.binding.BindingNode;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.model.Binding;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingKind;
import dagger.model.ComponentPath;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import dagger.spi.model.DaggerElement;
import dagger.spi.model.DaggerTypeElement;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

/** Reports errors for conflicting bindings with the same key. */
final class DuplicateBindingsValidator implements BindingGraphPlugin {

  private static final Comparator<Binding> BY_LENGTH_OF_COMPONENT_PATH =
      comparing(binding -> binding.componentPath().components().size());

  private final BindingDeclarationFormatter bindingDeclarationFormatter;
  private final CompilerOptions compilerOptions;

  @Inject
  DuplicateBindingsValidator(
      BindingDeclarationFormatter bindingDeclarationFormatter, CompilerOptions compilerOptions) {
    this.bindingDeclarationFormatter = bindingDeclarationFormatter;
    this.compilerOptions = compilerOptions;
  }

  @Override
  public String pluginName() {
    return "Dagger/DuplicateBindings";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    // If two unrelated subcomponents have the same duplicate bindings only because they install the
    // same two modules, then fixing the error in one subcomponent will uncover the second
    // subcomponent to fix.
    // TODO(ronshapiro): Explore ways to address such underreporting without overreporting.
    Set<Set<BindingElement>> reportedDuplicateBindingSets = new HashSet<>();
    duplicateBindingSets(bindingGraph)
        .forEach(
            duplicateBindings -> {
              // Only report each set of duplicate bindings once, ignoring the installed component.
              if (reportedDuplicateBindingSets.add(duplicateBindings.keySet())) {
                reportDuplicateBindings(duplicateBindings, bindingGraph, diagnosticReporter);
              }
            });
  }

  /**
   * Returns sets of duplicate bindings. Bindings are duplicates if they bind the same key and are
   * visible from the same component. Two bindings that differ only in the component that owns them
   * are not considered to be duplicates, because that means the same binding was "copied" down to a
   * descendant component because it depends on local multibindings or optional bindings. Hence each
   * "set" is represented as a multimap from binding element (ignoring component path) to binding.
   */
  private Set<Map<BindingElement, Set<Binding>>> duplicateBindingSets(
      BindingGraph bindingGraph) {
    return groupBindingsByKey(bindingGraph).stream()
        .flatMap(bindings -> mutuallyVisibleSubsets(bindings).stream())
        .map(BindingElement::index)
        .filter(duplicates -> duplicates.keySet().size() > 1)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static Set<Set<Binding>> groupBindingsByKey(BindingGraph bindingGraph) {
    return valueSetsForEachKey(
        bindingGraph.bindings().stream()
            .collect(toImmutableSetMultimap(Binding::key, binding -> binding)));
  }

  /**
   * Returns the subsets of the input set that contain bindings that are all visible from the same
   * component. A binding is visible from its component and all its descendants.
   */
  private static Set<Set<Binding>> mutuallyVisibleSubsets(
      Set<Binding> duplicateBindings) {
    Map<ComponentPath, List<Binding>> bindingsByComponentPath = duplicateBindings.stream()
        .collect(Collectors.groupingBy(Binding::componentPath, LinkedHashMap::new, Collectors.toList()));
    Map<ComponentPath, Set<Binding>> mutuallyVisibleBindings =
        new LinkedHashMap<>();
    bindingsByComponentPath
        .forEach(
            (componentPath, bindings) -> {
              mutuallyVisibleBindings.merge(componentPath, new LinkedHashSet<>(bindings), Util::mutableUnion);
              for (ComponentPath ancestor = componentPath; !ancestor.atRoot(); ) {
                ancestor = ancestor.parent();
                List<Binding> bindingsInAncestor = bindingsByComponentPath.getOrDefault(ancestor, List.of());
                mutuallyVisibleBindings.merge(componentPath, new LinkedHashSet<>(bindingsInAncestor), Util::mutableUnion);
              }
            });
    return valueSetsForEachKey(mutuallyVisibleBindings);
  }

  private void reportDuplicateBindings(
      Map<BindingElement, Set<Binding>> duplicateBindings,
      BindingGraph bindingGraph,
      DiagnosticReporter diagnosticReporter) {
    if (explicitBindingConflictsWithInject(duplicateBindings.keySet())) {
      compilerOptions
          .explicitBindingConflictsWithInjectValidationType()
          .diagnosticKind()
          .ifPresent(
              diagnosticKind ->
                  reportExplicitBindingConflictsWithInject(
                      duplicateBindings,
                      diagnosticReporter,
                      diagnosticKind,
                      bindingGraph.rootComponentNode()));
      return;
    }
    Set<Binding> bindings = duplicateBindings.values().stream()
        .flatMap(Set::stream)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    Binding oneBinding = bindings.iterator().next();
    String message = duplicateBindingMessage(oneBinding, bindings, bindingGraph);
    if (compilerOptions.experimentalDaggerErrorMessages()) {
      diagnosticReporter.reportComponent(
          ERROR,
          bindingGraph.rootComponentNode(),
          message);
    } else {
      diagnosticReporter.reportBinding(
          ERROR,
          oneBinding,
          message);
    }
  }

  /**
   * Returns {@code true} if the bindings contain one {@code @Inject} binding and one that isn't.
   */
  private static boolean explicitBindingConflictsWithInject(
      Set<BindingElement> duplicateBindings) {
    Map<BindingKind, List<BindingElement>> bindingKinds = duplicateBindings.stream()
        .collect(Collectors.groupingBy(BindingElement::bindingKind));
    List<BindingElement> injectBindings = bindingKinds.getOrDefault(INJECTION, List.of());
    return !injectBindings.isEmpty() && bindingKinds.size() > injectBindings.size();
  }

  private void reportExplicitBindingConflictsWithInject(
      Map<BindingElement, Set<Binding>> duplicateBindings,
      DiagnosticReporter diagnosticReporter,
      Kind diagnosticKind,
      ComponentNode rootComponent) {
    List<Binding> bindings = duplicateBindings.values().stream().flatMap(Set::stream).collect(Collectors.toList());
    Binding injectBinding =
        rootmostBindingWithKind(k -> k.equals(INJECTION), bindings);
    Binding explicitBinding =
        rootmostBindingWithKind(k -> !k.equals(INJECTION), bindings);
    StringBuilder message =
        new StringBuilder()
            .append(explicitBinding.key())
            .append(" is bound multiple times:")
            .append(formatWithComponentPath(injectBinding))
            .append(formatWithComponentPath(explicitBinding))
            .append(
                "\nThis condition was never validated before, and will soon be an error. "
                    + "See https://dagger.dev/conflicting-inject.");

    if (compilerOptions.experimentalDaggerErrorMessages()) {
      diagnosticReporter.reportComponent(diagnosticKind, rootComponent, message.toString());
    } else {
      diagnosticReporter.reportBinding(diagnosticKind, explicitBinding, message.toString());
    }
  }

  private String formatWithComponentPath(Binding binding) {
    return String.format(
        "\n%s%s [%s]",
        Formatter.INDENT,
        bindingDeclarationFormatter.format(((BindingNode) binding).delegate()),
        binding.componentPath());
  }

  private String duplicateBindingMessage(
      Binding oneBinding, Set<Binding> duplicateBindings, BindingGraph graph) {
    StringBuilder message =
        new StringBuilder().append(oneBinding.key()).append(" is bound multiple times:");
    formatDeclarations(message, declarations(graph, duplicateBindings));
    if (compilerOptions.experimentalDaggerErrorMessages()) {
      message.append(String.format("\n%sin component: [%s]", INDENT, oneBinding.componentPath()));
    }
    return message.toString();
  }

  private void formatDeclarations(
      StringBuilder builder,
      Collection<? extends BindingDeclaration> bindingDeclarations) {
    bindingDeclarationFormatter.formatIndentedList(
        builder, List.copyOf(bindingDeclarations), 1);
  }

  private Set<BindingDeclaration> declarations(
      BindingGraph graph, Set<Binding> bindings) {
    return bindings.stream()
        .flatMap(binding -> declarations(graph, binding).stream())
        .sorted(BindingDeclaration.COMPARATOR)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Set<BindingDeclaration> declarations(
      BindingGraph graph, Binding binding) {
    BindingNode bindingNode = (BindingNode) binding;
    Set<BindingDeclaration> declarations = bindingNode.associatedDeclarations();
    if (bindingDeclarationFormatter.canFormat(bindingNode.delegate())) {
      declarations.add(bindingNode.delegate());
    } else {
      graph.requestedBindings(binding).stream()
          .flatMap(requestedBinding -> declarations(graph, requestedBinding).stream())
          .forEach(declarations::add);
    }
    return declarations;
  }

  private static <E> Set<Set<E>> valueSetsForEachKey(Map<?, Set<E>> multimap) {
    return new LinkedHashSet<>(multimap.values());
  }

  /** Returns the binding of the given kind that is closest to the root component. */
  private static Binding rootmostBindingWithKind(
      Predicate<BindingKind> bindingKindPredicate, List<Binding> bindings) {
    return bindings.stream()
        .filter(b -> bindingKindPredicate.test(b.kind()))
        .min(BY_LENGTH_OF_COMPONENT_PATH)
        .orElseThrow();
  }

  /** The identifying information about a binding, excluding its {@link Binding#componentPath()}. */
  static final class BindingElement {
    private final BindingKind bindingKind;
    private final Optional<Element> bindingElement;
    private final Optional<TypeElement> contributingModule;

    BindingElement(
        BindingKind bindingKind,
        Optional<Element> bindingElement,
        Optional<TypeElement> contributingModule) {
      this.bindingKind = requireNonNull(bindingKind);
      this.bindingElement = requireNonNull(bindingElement);
      this.contributingModule = requireNonNull(contributingModule);
    }

    BindingKind bindingKind() {
      return bindingKind;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      BindingElement that = (BindingElement) o;
      return bindingKind == that.bindingKind
          && bindingElement.equals(that.bindingElement)
          && contributingModule.equals(that.contributingModule);
    }

    @Override
    public int hashCode() {
      return Objects.hash(bindingKind, bindingElement, contributingModule);
    }

    static Map<BindingElement, Set<Binding>> index(Set<Binding> bindings) {
      return bindings.stream().collect(toImmutableSetMultimap(BindingElement::forBinding, b -> b));
    }

    private static BindingElement forBinding(Binding binding) {
      return new BindingElement(
          binding.kind(),
          binding.bindingElement().map(DaggerElement::java),
          binding.contributingModule().map(DaggerTypeElement::java));
    }
  }
}
