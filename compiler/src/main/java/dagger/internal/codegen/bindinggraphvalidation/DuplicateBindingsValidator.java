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
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSetMultimap;
import static dagger.model.BindingKind.INJECTION;
import static dagger.model.BindingKind.MEMBERS_INJECTION;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import dagger.internal.codegen.base.Formatter;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.binding.BindingDeclaration;
import dagger.internal.codegen.binding.BindingDeclarationFormatter;
import dagger.internal.codegen.binding.BindingNode;
import dagger.internal.codegen.binding.MultibindingDeclaration;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.extension.DaggerStreams;
import dagger.model.Binding;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingKind;
import dagger.model.ComponentPath;
import dagger.model.Key;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
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
  private Set<ImmutableSetMultimap<BindingElement, Binding>> duplicateBindingSets(
      BindingGraph bindingGraph) {
    return groupBindingsByKey(bindingGraph).stream()
        .flatMap(bindings -> mutuallyVisibleSubsets(bindings).stream())
        .map(BindingElement::index)
        .filter(duplicates -> duplicates.keySet().size() > 1)
        .collect(toImmutableSet());
  }

  private static Set<Set<Binding>> groupBindingsByKey(BindingGraph bindingGraph) {
    return valueSetsForEachKey(
        bindingGraph.bindings().stream()
            .filter(binding -> !binding.kind().equals(MEMBERS_INJECTION))
            .collect(toImmutableSetMultimap(Binding::key, binding -> binding)));
  }

  /**
   * Returns the subsets of the input set that contain bindings that are all visible from the same
   * component. A binding is visible from its component and all its descendants.
   */
  private static Set<Set<Binding>> mutuallyVisibleSubsets(
      Set<Binding> duplicateBindings) {
    ImmutableListMultimap<ComponentPath, Binding> bindingsByComponentPath =
        Multimaps.index(duplicateBindings, Binding::componentPath);
    ImmutableSetMultimap.Builder<ComponentPath, Binding> mutuallyVisibleBindings =
        ImmutableSetMultimap.builder();
    bindingsByComponentPath
        .asMap()
        .forEach(
            (componentPath, bindings) -> {
              mutuallyVisibleBindings.putAll(componentPath, bindings);
              for (ComponentPath ancestor = componentPath; !ancestor.atRoot(); ) {
                ancestor = ancestor.parent();
                List<Binding> bindingsInAncestor = bindingsByComponentPath.get(ancestor);
                mutuallyVisibleBindings.putAll(componentPath, bindingsInAncestor);
              }
            });
    return valueSetsForEachKey(mutuallyVisibleBindings.build());
  }

  private void reportDuplicateBindings(
      ImmutableSetMultimap<BindingElement, Binding> duplicateBindings,
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
    Set<Binding> bindings = new LinkedHashSet<>(duplicateBindings.values());
    Binding oneBinding = bindings.iterator().next();
    String message = bindings.stream().anyMatch(binding -> binding.kind().isMultibinding())
        ? incompatibleBindingsMessage(oneBinding, bindings, bindingGraph)
        : duplicateBindingMessage(oneBinding, bindings, bindingGraph);
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
      ImmutableSetMultimap<BindingElement, Binding> duplicateBindings,
      DiagnosticReporter diagnosticReporter,
      Kind diagnosticKind,
      ComponentNode rootComponent) {
    Binding injectBinding =
        rootmostBindingWithKind(k -> k.equals(INJECTION), duplicateBindings.values());
    Binding explicitBinding =
        rootmostBindingWithKind(k -> !k.equals(INJECTION), duplicateBindings.values());
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
    formatDeclarations(message, 1, declarations(graph, duplicateBindings));
    if (compilerOptions.experimentalDaggerErrorMessages()) {
      message.append(String.format("\n%sin component: [%s]", INDENT, oneBinding.componentPath()));
    }
    return message.toString();
  }

  private String incompatibleBindingsMessage(
      Binding oneBinding, Set<Binding> duplicateBindings, BindingGraph graph) {
    Key key = oneBinding.key();
    Set<Binding> multibindings =
        duplicateBindings.stream()
            .filter(binding -> binding.kind().isMultibinding())
            .collect(toImmutableSet());
    Preconditions.checkState(
        multibindings.size() == 1, "expected only one multibinding for %s: %s", key, multibindings);
    StringBuilder message = new StringBuilder();
    java.util.Formatter messageFormatter = new java.util.Formatter(message);
    messageFormatter.format("%s has incompatible bindings or declarations:\n", key);
    message.append(INDENT);
    Binding multibinding = Util.getOnlyElement(multibindings);
    messageFormatter.format("%s bindings and declarations:", multibindingTypeString(multibinding));
    formatDeclarations(message, 2, declarations(graph, multibindings));

    Set<Binding> uniqueBindings =
        duplicateBindings.stream().filter(binding -> !binding.equals(multibinding))
            .collect(DaggerStreams.toImmutableSet());
    message.append('\n').append(INDENT).append("Unique bindings and declarations:");
    formatDeclarations(
        message,
        2,
        declarations(graph, uniqueBindings).stream().filter(declaration -> !(declaration instanceof MultibindingDeclaration))
            .collect(DaggerStreams.toImmutableSet()));
    if (compilerOptions.experimentalDaggerErrorMessages()) {
      message.append(String.format("\n%sin component: [%s]", INDENT, oneBinding.componentPath()));
    }
    return message.toString();
  }

  private void formatDeclarations(
      StringBuilder builder,
      int indentLevel,
      Collection<? extends BindingDeclaration> bindingDeclarations) {
    bindingDeclarationFormatter.formatIndentedList(
        builder, List.copyOf(bindingDeclarations), indentLevel);
  }

  private Set<BindingDeclaration> declarations(
      BindingGraph graph, Set<Binding> bindings) {
    return bindings.stream()
        .flatMap(binding -> declarations(graph, binding).stream())
        .distinct()
        .sorted(BindingDeclaration.COMPARATOR)
        .collect(toImmutableSet());
  }

  private Set<BindingDeclaration> declarations(
      BindingGraph graph, Binding binding) {
    BindingNode bindingNode = (BindingNode) binding;
    Set<BindingDeclaration> declarations = new LinkedHashSet<>(bindingNode.associatedDeclarations());
    if (bindingDeclarationFormatter.canFormat(bindingNode.delegate())) {
      declarations.add(bindingNode.delegate());
    } else {
      graph.requestedBindings(binding).stream()
          .flatMap(requestedBinding -> declarations(graph, requestedBinding).stream())
          .forEach(declarations::add);
    }
    return declarations;
  }

  private String multibindingTypeString(Binding multibinding) {
    switch (multibinding.kind()) {
      case MULTIBOUND_MAP:
        return "Map";
      case MULTIBOUND_SET:
        return "Set";
      default:
        throw new AssertionError(multibinding);
    }
  }

  private static <E> Set<Set<E>> valueSetsForEachKey(Multimap<?, E> multimap) {
    return multimap.asMap().values().stream().map(LinkedHashSet::new).collect(toImmutableSet());
  }

  /** Returns the binding of the given kind that is closest to the root component. */
  private static Binding rootmostBindingWithKind(
      Predicate<BindingKind> bindingKindPredicate, Collection<Binding> bindings) {
    return bindings.stream()
        .filter(b -> bindingKindPredicate.test(b.kind()))
        .min(BY_LENGTH_OF_COMPONENT_PATH)
        .get();
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

    static ImmutableSetMultimap<BindingElement, Binding> index(Set<Binding> bindings) {
      return bindings.stream().collect(toImmutableSetMultimap(BindingElement::forBinding, b -> b));
    }

    private static BindingElement forBinding(Binding binding) {
      return new BindingElement(
          binding.kind(), binding.bindingElement(), binding.contributingModule());
    }
  }
}
