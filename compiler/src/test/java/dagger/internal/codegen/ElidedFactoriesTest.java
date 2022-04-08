/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen;

import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static io.jbock.testing.compile.CompilationSubject.assertThat;

import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Disabled
class ElidedFactoriesTest {

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  public void simpleComponent(CompilerMode compilerMode) {
    JavaFileObject injectedType =
        JavaFileObjects.forSourceLines(
            "test.InjectedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class InjectedType {",
            "  @Inject InjectedType() {}",
            "}");

    JavaFileObject dependsOnInjected =
        JavaFileObjects.forSourceLines(
            "test.InjectedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class DependsOnInjected {",
            "  @Inject DependsOnInjected(InjectedType injected) {}",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface SimpleComponent {",
            "  DependsOnInjected dependsOnInjected();",
            "}");
    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedImports());
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(generatedComponent,
        "final class DaggerSimpleComponent implements SimpleComponent {",
        "  private final DaggerSimpleComponent simpleComponent = this;",
        "",
        "  private DaggerSimpleComponent() {",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static SimpleComponent create() {",
        "    return new Builder().build();",
        "  }",
        "",
        "  @Override",
        "  public DependsOnInjected dependsOnInjected() {",
        "    return new DependsOnInjected(new InjectedType());",
        "  }",
        "",
        "  static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public SimpleComponent build() {",
        "      return new DaggerSimpleComponent();",
        "    }",
        "  }",
        "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectedType, dependsOnInjected, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  public void simpleComponent_injectsProviderOf_dependsOnScoped(CompilerMode compilerMode) {
    JavaFileObject scopedType =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "final class ScopedType {",
            "  @Inject ScopedType() {}",
            "}");

    JavaFileObject dependsOnScoped =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Provider;",
            "",
            "final class DependsOnScoped {",
            "  @Inject DependsOnScoped(ScopedType scoped) {}",
            "}");

    JavaFileObject needsProvider =
        JavaFileObjects.forSourceLines(
            "test.NeedsProvider",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Provider;",
            "",
            "class NeedsProvider {",
            "  @Inject NeedsProvider(Provider<DependsOnScoped> provider) {}",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface SimpleComponent {",
            "  NeedsProvider needsProvider();",
            "}");
    List<String> generatedComponent;
    if (compilerMode == CompilerMode.FAST_INIT_MODE) {
      generatedComponent = new ArrayList<>();
      Collections.addAll(generatedComponent,
          "package test;");
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedImports(
              "import dagger.internal.DoubleCheck;",
              "import jakarta.inject.Provider;"));
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedAnnotations());
      Collections.addAll(generatedComponent,
          "final class DaggerSimpleComponent implements SimpleComponent {",
          "  private Provider<ScopedType> scopedTypeProvider;",
          "  private Provider<DependsOnScoped> dependsOnScopedProvider;",
          "",
          "  @SuppressWarnings(\"unchecked\")",
          "  private void initialize() {",
          "    this.scopedTypeProvider = DoubleCheck.provider(new SwitchingProvider<ScopedType>(simpleComponent, 1));",
          "    this.dependsOnScopedProvider = new SwitchingProvider<>(simpleComponent, 0);",
          "  }",
          "",
          "  private static final class SwitchingProvider<T> implements Provider<T> {",
          "    @SuppressWarnings(\"unchecked\")",
          "    @Override",
          "    public T get() {",
          "      switch (id) {",
          "        case 0: // test.DependsOnScoped ",
          "        return (T) new DependsOnScoped(simpleComponent.scopedTypeProvider.get());",
          "        case 1: // test.ScopedType ",
          "        return (T) new ScopedType();",
          "        default: throw new AssertionError(id);",
          "      }",
          "    }",
          "  }");
    } else {
      generatedComponent = new ArrayList<>();
      Collections.addAll(generatedComponent,
          "package test;");
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedImports(
              "import dagger.internal.DoubleCheck;",
              "import jakarta.inject.Provider;"));
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedAnnotations());
      Collections.addAll(generatedComponent,
          "final class DaggerSimpleComponent implements SimpleComponent {",
          "  private Provider<ScopedType> scopedTypeProvider;",
          "  private Provider<DependsOnScoped> dependsOnScopedProvider;",
          "  private final DaggerSimpleComponent simpleComponent = this;",
          "",
          "  @Override",
          "  public NeedsProvider needsProvider() {",
          "    return new NeedsProvider(dependsOnScopedProvider);",
          "  }",
          "",
          "  @SuppressWarnings(\"unchecked\")",
          "  private void initialize() {",
          "    this.scopedTypeProvider = DoubleCheck.provider(ScopedType_Factory.create());",
          "    this.dependsOnScopedProvider = DependsOnScoped_Factory.create(scopedTypeProvider);",
          "  }",
          "");
    }
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(scopedType, dependsOnScoped, componentFile, needsProvider);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  public void scopedBinding_onlyUsedInSubcomponent(CompilerMode compilerMode) {
    JavaFileObject scopedType =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "final class ScopedType {",
            "  @Inject ScopedType() {}",
            "}");

    JavaFileObject dependsOnScoped =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Provider;",
            "",
            "final class DependsOnScoped {",
            "  @Inject DependsOnScoped(ScopedType scoped) {}",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface SimpleComponent {",
            "  Sub sub();",
            "}");
    JavaFileObject subcomponentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Sub {",
            "  DependsOnScoped dependsOnScoped();",
            "}");

    List<String> generatedComponent;
    if (compilerMode == CompilerMode.FAST_INIT_MODE) {
      generatedComponent = new ArrayList<>();
      Collections.addAll(generatedComponent,
          "package test;");
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedImports(
              "import dagger.internal.DoubleCheck;"));
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedAnnotations());
      Collections.addAll(generatedComponent,
          "final class DaggerSimpleComponent implements SimpleComponent {",
          "  private Provider<ScopedType> scopedTypeProvider;",
          "  private final DaggerSimpleComponent simpleComponent = this;",
          "",
          "  @Override",
          "  public Sub sub() {",
          "    return new SubImpl(simpleComponent);",
          "  }",
          "",
          "  @SuppressWarnings(\"unchecked\")",
          "  private void initialize() {",
          "    this.scopedTypeProvider = DoubleCheck.provider(new SwitchingProvider<ScopedType>(simpleComponent, 0));",
          "  }",
          "",
          "  private static final class SubImpl implements Sub {",
          "    private final DaggerSimpleComponent simpleComponent;",
          "    private final SubImpl subImpl = this;",
          "",
          "    @Override",
          "    public DependsOnScoped dependsOnScoped() {",
          "      return new DependsOnScoped(simpleComponent.scopedTypeProvider.get());",
          "    }",
          "  }",
          "  private static final class SwitchingProvider<T> implements Provider<T> {",
          "    @SuppressWarnings(\"unchecked\")",
          "    @Override",
          "    public T get() {",
          "      switch (id) {",
          "        case 0: // test.ScopedType ",
          "        return (T) new ScopedType();",
          "        default: throw new AssertionError(id);",
          "      }",
          "    }",
          "  }");
    } else {
      generatedComponent = new ArrayList<>();
      Collections.addAll(generatedComponent,
          "package test;");
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedImports(
              "import dagger.internal.DoubleCheck;",
              "import jakarta.inject.Provider;"));
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedAnnotations());
      Collections.addAll(generatedComponent,
          "final class DaggerSimpleComponent implements SimpleComponent {",
          "  private Provider<ScopedType> scopedTypeProvider;",
          "  private final DaggerSimpleComponent simpleComponent = this;",
          "",
          "  private DaggerSimpleComponent() {",
          "    initialize();",
          "  }",
          "",
          "  public static Builder builder() {",
          "    return new Builder();",
          "  }",
          "",
          "  public static SimpleComponent create() {",
          "    return new Builder().build();",
          "  }",
          "",
          "  @Override",
          "  public Sub sub() {",
          "    return new SubImpl(simpleComponent);",
          "  }",
          "",
          "  @SuppressWarnings(\"unchecked\")",
          "  private void initialize() {",
          "    this.scopedTypeProvider = DoubleCheck.provider(ScopedType_Factory.create());",
          "  }",
          "",
          "  static final class Builder {",
          "    private Builder() {",
          "    }",
          "",
          "    public SimpleComponent build() {",
          "      return new DaggerSimpleComponent();",
          "    }",
          "  }",
          "",
          "  private static final class SubImpl implements Sub {",
          "    private final DaggerSimpleComponent simpleComponent;",
          "",
          "    private final SubImpl subImpl = this;",
          "",
          "    private SubImpl(DaggerSimpleComponent simpleComponent) {",
          "      this.simpleComponent = simpleComponent;",
          "    }",
          "",
          "    @Override",
          "    public DependsOnScoped dependsOnScoped() {",
          "      return new DependsOnScoped(simpleComponent.scopedTypeProvider.get());",
          "    }",
          "  }",
          "}");
    }
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(scopedType, dependsOnScoped, componentFile, subcomponentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(generatedComponent);
  }
}
