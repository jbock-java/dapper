/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.internal.codegen.compileroption;

import static dagger.internal.codegen.compileroption.FeatureStatus.DISABLED;
import static dagger.internal.codegen.compileroption.FeatureStatus.ENABLED;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.EXPERIMENTAL_AHEAD_OF_TIME_SUBCOMPONENTS;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.EXPERIMENTAL_ANDROID_MODE;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.EXPERIMENTAL_DAGGER_ERROR_MESSAGES;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.FAST_INIT;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.FLOATING_BINDS_METHODS;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.IGNORE_PRIVATE_AND_STATIC_INJECTION_FOR_COMPONENT;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.PLUGINS_VISIT_FULL_BINDING_GRAPHS;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.VALIDATE_TRANSITIVE_COMPONENT_DEPENDENCIES;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Feature.WARN_IF_INJECTION_FACTORY_NOT_GENERATED_UPSTREAM;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.KeyOnlyOption.USE_GRADLE_INCREMENTAL_PROCESSING;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.DISABLE_INTER_COMPONENT_SCOPE_VALIDATION;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.EXPLICIT_BINDING_CONFLICTS_WITH_INJECT;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.FULL_BINDING_GRAPH_VALIDATION;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.MODULE_HAS_DIFFERENT_SCOPES_VALIDATION;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.NULLABLE_VALIDATION;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.PRIVATE_MEMBER_VALIDATION;
import static dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions.Validation.STATIC_MEMBER_VALIDATION;
import static dagger.internal.codegen.compileroption.ValidationType.ERROR;
import static dagger.internal.codegen.compileroption.ValidationType.NONE;
import static dagger.internal.codegen.compileroption.ValidationType.WARNING;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.concat;

import com.google.auto.common.MoreElements;
import dagger.internal.codegen.base.Preconditions;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/** {@link CompilerOptions} for the given {@link ProcessingEnvironment}. */
public final class ProcessingEnvironmentCompilerOptions extends CompilerOptions {
  // EnumOption<T> doesn't support integer inputs so just doing this as a 1-off for now.
  private static final String KEYS_PER_COMPONENT_SHARD = "dagger.keysPerComponentShard";

  private final ProcessingEnvironment processingEnvironment;
  private final Map<EnumOption<?>, Object> enumOptions = new HashMap<>();
  private final Map<EnumOption<?>, Map<String, ? extends Enum<?>>> allCommandLineOptions =
      new HashMap<>();

  @Inject
  ProcessingEnvironmentCompilerOptions(
      ProcessingEnvironment processingEnvironment) {
    this.processingEnvironment = processingEnvironment;
    checkValid();
  }

  @Override
  public boolean fastInit(TypeElement component) {
    return isEnabled(FAST_INIT);
  }

  @Override
  public Diagnostic.Kind nullableValidationKind() {
    return diagnosticKind(NULLABLE_VALIDATION);
  }

  @Override
  public Diagnostic.Kind privateMemberValidationKind() {
    return diagnosticKind(PRIVATE_MEMBER_VALIDATION);
  }

  @Override
  public boolean ignorePrivateAndStaticInjectionForComponent() {
    return isEnabled(IGNORE_PRIVATE_AND_STATIC_INJECTION_FOR_COMPONENT);
  }

  @Override
  public ValidationType scopeCycleValidationType() {
    return parseOption(DISABLE_INTER_COMPONENT_SCOPE_VALIDATION);
  }

  @Override
  public boolean validateTransitiveComponentDependencies() {
    return isEnabled(VALIDATE_TRANSITIVE_COMPONENT_DEPENDENCIES);
  }

  @Override
  public boolean warnIfInjectionFactoryNotGeneratedUpstream() {
    return isEnabled(WARN_IF_INJECTION_FACTORY_NOT_GENERATED_UPSTREAM);
  }

  @Override
  public ValidationType fullBindingGraphValidationType() {
    return parseOption(FULL_BINDING_GRAPH_VALIDATION);
  }

  @Override
  public boolean pluginsVisitFullBindingGraphs(TypeElement component) {
    return isEnabled(PLUGINS_VISIT_FULL_BINDING_GRAPHS);
  }

  @Override
  public Diagnostic.Kind moduleHasDifferentScopesDiagnosticKind() {
    return diagnosticKind(MODULE_HAS_DIFFERENT_SCOPES_VALIDATION);
  }

  @Override
  public ValidationType explicitBindingConflictsWithInjectValidationType() {
    return parseOption(EXPLICIT_BINDING_CONFLICTS_WITH_INJECT);
  }

  @Override
  public boolean experimentalDaggerErrorMessages() {
    return isEnabled(EXPERIMENTAL_DAGGER_ERROR_MESSAGES);
  }

  @Override
  public int keysPerComponentShard(TypeElement component) {
    if (processingEnvironment.getOptions().containsKey(KEYS_PER_COMPONENT_SHARD)) {
      Preconditions.checkArgument(
          MoreElements.getPackage(component).getQualifiedName().toString().startsWith("dagger."),
          "Cannot set %s. It is only meant for internal testing.", KEYS_PER_COMPONENT_SHARD);
      return Integer.parseInt(processingEnvironment.getOptions().get(KEYS_PER_COMPONENT_SHARD));
    }
    return super.keysPerComponentShard(component);
  }

  private boolean isEnabled(Feature feature) {
    return parseOption(feature).equals(ENABLED);
  }

  private Diagnostic.Kind diagnosticKind(Validation validation) {
    return parseOption(validation).diagnosticKind().orElseThrow();
  }

  private void checkValid() {
    for (Feature feature : Feature.values()) {
      parseOption(feature);
    }
    for (Validation validation : Validation.values()) {
      parseOption(validation);
    }
    noLongerRecognized(EXPERIMENTAL_ANDROID_MODE);
    noLongerRecognized(FLOATING_BINDS_METHODS);
    noLongerRecognized(EXPERIMENTAL_AHEAD_OF_TIME_SUBCOMPONENTS);
    noLongerRecognized(USE_GRADLE_INCREMENTAL_PROCESSING);
  }

  private void noLongerRecognized(CommandLineOption commandLineOption) {
    if (processingEnvironment.getOptions().containsKey(commandLineOption.toString())) {
      processingEnvironment
          .getMessager()
          .printMessage(
              Diagnostic.Kind.WARNING, commandLineOption + " is no longer recognized by Dagger");
    }
  }

  private interface CommandLineOption {
    /** The key of the option (appears after "-A"). */
    @Override
    String toString();

    /**
     * Returns all aliases besides {@link #toString()}, such as old names for an option, in order of
     * precedence.
     */
    default List<String> aliases() {
      return List.of();
    }

    /** All the command-line names for this option, in order of precedence. */
    default Stream<String> allNames() {
      return concat(Stream.of(toString()), aliases().stream());
    }
  }

  /** An option that can be set on the command line. */
  private interface EnumOption<E extends Enum<E>> extends CommandLineOption {
    /** The default value for this option. */
    E defaultValue();

    /** The valid values for this option. */
    Set<E> validValues();
  }

  enum KeyOnlyOption implements CommandLineOption {

    USE_GRADLE_INCREMENTAL_PROCESSING {
      @Override
      public String toString() {
        return "dagger.gradle.incremental";
      }
    },
  }

  /**
   * A feature that can be enabled or disabled on the command line by setting {@code -Akey=ENABLED}
   * or {@code -Akey=DISABLED}.
   */
  enum Feature implements EnumOption<FeatureStatus> {
    FAST_INIT("fastInit"),

    EXPERIMENTAL_ANDROID_MODE("experimentalAndroidMode"),

    FORMAT_GENERATED_SOURCE("formatGeneratedSource"),

    WARN_IF_INJECTION_FACTORY_NOT_GENERATED_UPSTREAM("warnIfInjectionFactoryNotGeneratedUpstream"),

    IGNORE_PRIVATE_AND_STATIC_INJECTION_FOR_COMPONENT("ignorePrivateAndStaticInjectionForComponent"),

    EXPERIMENTAL_AHEAD_OF_TIME_SUBCOMPONENTS("experimentalAheadOfTimeSubcomponents"),

    FORCE_USE_SERIALIZED_COMPONENT_IMPLEMENTATIONS("forceUseSerializedComponentImplementations"),

    EMIT_MODIFIABLE_METADATA_ANNOTATIONS("emitModifiableMetadataAnnotations", ENABLED),

    PLUGINS_VISIT_FULL_BINDING_GRAPHS("pluginsVisitFullBindingGraphs"),

    FLOATING_BINDS_METHODS("floatingBindsMethods"),

    EXPERIMENTAL_DAGGER_ERROR_MESSAGES("experimentalDaggerErrorMessages"),

    STRICT_MULTIBINDING_VALIDATION("strictMultibindingValidation"),

    VALIDATE_TRANSITIVE_COMPONENT_DEPENDENCIES("validateTransitiveComponentDependencies", ENABLED);

    final String optionKey;
    final FeatureStatus defaultValue;

    Feature(String optionKey) {
      this(optionKey, DISABLED);
    }

    Feature(String optionKey, FeatureStatus defaultValue) {
      this.optionKey = optionKey;
      this.defaultValue = defaultValue;
    }

    @Override
    public FeatureStatus defaultValue() {
      return defaultValue;
    }

    @Override
    public Set<FeatureStatus> validValues() {
      return EnumSet.allOf(FeatureStatus.class);
    }

    @Override
    public String toString() {
      return "dagger." + optionKey;
    }
  }

  /** The diagnostic kind or validation type for a kind of validation. */
  enum Validation implements EnumOption<ValidationType> {
    DISABLE_INTER_COMPONENT_SCOPE_VALIDATION("disableInterComponentScopeValidation"),

    NULLABLE_VALIDATION("nullableValidation", ERROR, WARNING),

    PRIVATE_MEMBER_VALIDATION("privateMemberValidation", ERROR, WARNING),

    STATIC_MEMBER_VALIDATION("staticMemberValidation", ERROR, WARNING),

    /** Whether to validate full binding graphs for components, subcomponents, and modules. */
    FULL_BINDING_GRAPH_VALIDATION("fullBindingGraphValidation", NONE, ERROR, WARNING) {
      @Override
      public List<String> aliases() {
        return List.of("dagger.moduleBindingValidation");
      }
    },

    /**
     * How to report conflicting scoped bindings when validating partial binding graphs associated
     * with modules.
     */
    MODULE_HAS_DIFFERENT_SCOPES_VALIDATION("moduleHasDifferentScopesValidation", ERROR, WARNING),

    /**
     * How to report that an explicit binding in a subcomponent conflicts with an {@code @Inject}
     * constructor used in an ancestor component.
     */
    EXPLICIT_BINDING_CONFLICTS_WITH_INJECT("explicitBindingConflictsWithInject", WARNING, ERROR, NONE),
    ;

    final String optionKey;
    final ValidationType defaultType;
    final Set<ValidationType> validTypes;

    Validation(String optionKey) {
      this(optionKey, ERROR, WARNING, NONE);
    }

    Validation(String optionKey, ValidationType defaultType, ValidationType... moreValidTypes) {
      this.optionKey = optionKey;
      this.defaultType = defaultType;
      this.validTypes = EnumSet.of(defaultType, moreValidTypes);
    }

    @Override
    public ValidationType defaultValue() {
      return defaultType;
    }

    @Override
    public Set<ValidationType> validValues() {
      return validTypes;
    }

    @Override
    public String toString() {
      return "dagger." + optionKey;
    }
  }

  /** The supported command-line options. */
  public static Set<String> supportedOptions() {
    return Stream.of(
            Stream.<CommandLineOption[]>of(
                    KeyOnlyOption.values(), Feature.values(), Validation.values())
                .flatMap(Arrays::stream)
                .flatMap(CommandLineOption::allNames),
            Stream.of(KEYS_PER_COMPONENT_SHARD))
        .flatMap(Function.identity())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Returns the value for the option as set on the command line by any name, or the default value
   * if not set.
   *
   * <p>If more than one name is used to set the value, but all names specify the same value,
   * reports a warning and returns that value.
   *
   * <p>If more than one name is used to set the value, and not all names specify the same value,
   * reports an error and returns the default value.
   */
  private <T extends Enum<T>> T parseOption(EnumOption<T> option) {
    @SuppressWarnings("unchecked") // we only put covariant values into the map
    T value = (T) enumOptions.computeIfAbsent(option, this::parseOptionUncached);
    return value;
  }

  private <T extends Enum<T>> T parseOptionUncached(EnumOption<T> option) {
    Map<String, T> values = parseOptionWithAllNames(option);

    // If no value is specified, return the default value.
    if (values.isEmpty()) {
      return option.defaultValue();
    }

    // If all names have the same value, return that.
    if (new HashSet<>(values.values()).size() == 1) {
      // Warn if an option was set with more than one name. That would be an error if the values
      // differed.
      if (values.size() > 1) {
        reportUseOfDifferentNamesForOption(Diagnostic.Kind.WARNING, option, values.keySet());
      }
      return values.values().iterator().next();
    }

    // If different names have different values, report an error and return the default
    // value.
    reportUseOfDifferentNamesForOption(Diagnostic.Kind.ERROR, option, values.keySet());
    return option.defaultValue();
  }

  private void reportUseOfDifferentNamesForOption(
      Diagnostic.Kind diagnosticKind, EnumOption<?> option, Set<String> usedNames) {
    processingEnvironment
        .getMessager()
        .printMessage(
            diagnosticKind,
            String.format(
                "Only one of the equivalent options (%s) should be used; prefer -A%s",
                usedNames.stream().map(name -> "-A" + name).collect(joining(", ")), option));
  }

  private <T extends Enum<T>> Map<String, T> parseOptionWithAllNames(
      EnumOption<T> option) {
    @SuppressWarnings("unchecked") // map is covariant
    Map<String, T> aliasValues =
        (Map<String, T>)
            allCommandLineOptions.computeIfAbsent(option, this::parseOptionWithAllNamesUncached);
    return aliasValues;
  }

  private <T extends Enum<T>> Map<String, T> parseOptionWithAllNamesUncached(
      EnumOption<T> option) {
    Map<String, T> values = new LinkedHashMap<>();
    getUsedNames(option)
        .forEach(
            name -> parseOptionWithName(option, name).ifPresent(value -> values.put(name, value)));
    return values;
  }

  private <T extends Enum<T>> Optional<T> parseOptionWithName(EnumOption<T> option, String key) {
    Preconditions.checkArgument(processingEnvironment.getOptions().containsKey(key), "key %s not found", key);
    String stringValue = processingEnvironment.getOptions().get(key);
    if (stringValue == null) {
      processingEnvironment
          .getMessager()
          .printMessage(Diagnostic.Kind.ERROR, "Processor option -A" + key + " needs a value");
    } else {
      try {
        T value =
            Enum.valueOf(option.defaultValue().getDeclaringClass(), stringValue.toUpperCase(Locale.ROOT));
        if (option.validValues().contains(value)) {
          return Optional.of(value);
        }
      } catch (IllegalArgumentException e) {
        // handled below
      }
      processingEnvironment
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              String.format(
                  "Processor option -A%s may only have the values %s "
                      + "(case insensitive), found: %s",
                  key, option.validValues(), stringValue));
    }
    return Optional.empty();
  }

  private Stream<String> getUsedNames(CommandLineOption option) {
    return option.allNames().filter(name -> processingEnvironment.getOptions().containsKey(name));
  }
}
