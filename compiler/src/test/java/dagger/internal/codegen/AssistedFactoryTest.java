/*
 * Copyright (C) 2020 The Dagger Authors.
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

import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static io.jbock.testing.compile.CompilationSubject.assertThat;

import dagger.testing.golden.GoldenFile;
import dagger.testing.golden.GoldenFileExtension;
import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@ExtendWith(GoldenFileExtension.class)
class AssistedFactoryTest {

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testAssistedFactory(CompilerMode compilerMode, GoldenFile goldenFile) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted String str, Bar bar) {}",
            "}");
    JavaFileObject fooFactory =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory {",
            "  Foo create(String factoryStr);",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts(false)).compile(foo, bar, fooFactory, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(goldenFile.get("test.DaggerTestComponent", compilerMode));
  }

  @Disabled
  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testAssistedFactoryCycle(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted String str, Bar bar) {}",
            "}");
    JavaFileObject fooFactory =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory {",
            "  Foo create(String factoryStr);",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar(FooFactory fooFactory) {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, bar, fooFactory, component);
    assertThat(compilation).succeeded();
    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines("package test;")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLinesIn(
                FAST_INIT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "  private Provider<FooFactory> fooFactoryProvider;",
                "  private final DaggerTestComponent testComponent = this;",
                "",
                "  private Bar bar() {",
                "    return new Bar(fooFactoryProvider.get());",
                "  }",
                "",
                "  @Override",
                "  public FooFactory fooFactory() {",
                "    return fooFactoryProvider.get();",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.fooFactoryProvider = SingleCheck.provider(new SwitchingProvider<FooFactory>(testComponent, 0));",
                "  }",
                "",
                "  private static final class SwitchingProvider<T> implements Provider<T> {",
                "    private final DaggerTestComponent testComponent;",
                "    private final int id;",
                "",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0: // test.FooFactory ",
                "        return (T) new FooFactory() {",
                "          @Override",
                "          public Foo create(String str) {",
                "            return new Foo(str, testComponent.bar());",
                "          }",
                "        };",
                "",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }",
                "  }",
                "}")
            .addLinesIn(
                DEFAULT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "  private Provider<FooFactory> fooFactoryProvider;",
                "  private Provider<Bar> barProvider;",
                "  private Foo_Factory fooProvider;",
                "",
                "  @Override",
                "  public FooFactory fooFactory() {",
                "    return fooFactoryProvider.get();",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.fooFactoryProvider = new DelegateFactory<>();",
                "    this.barProvider = Bar_Factory.create(fooFactoryProvider);",
                "    this.fooProvider = Foo_Factory.create(barProvider);",
                "    DelegateFactory.setDelegate(fooFactoryProvider, FooFactory_Impl.create(fooProvider));",
                "  }",
                "}")
            .build();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @Disabled
  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void assistedParamConflictsWithComponentFieldName_successfulyDeduped(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import jakarta.inject.Provider;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted String testComponent, Provider<Bar> bar) {}",
            "}");
    JavaFileObject fooFactory =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory {",
            "  Foo create(String factoryStr);",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");
    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines("package test;", "")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLinesIn(
                FAST_INIT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "  private Provider<FooFactory> fooFactoryProvider;",
                "  private final DaggerTestComponent testComponent = this;",
                "",
                "  @Override",
                "  public FooFactory fooFactory() {",
                "    return fooFactoryProvider.get();",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.barProvider = new SwitchingProvider<>(testComponent, 1);",
                "    this.fooFactoryProvider = SingleCheck.provider(new SwitchingProvider<FooFactory>(testComponent, 0));",
                "  }",
                "",
                "  private static final class SwitchingProvider<T> implements Provider<T> {",
                "    private final DaggerTestComponent testComponent;",
                "    private final int id;",
                "",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0: // test.FooFactory ",
                "        return (T) new FooFactory() {",
                "          @Override",
                "          public Foo create(String testComponent2) {",
                "            return new Foo(testComponent2, testComponent.barProvider);",
                "          }",
                "        };",
                "        case 1: // test.Bar ",
                "        return (T) new Bar();",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }",
                "  }",
                "}")
            .addLinesIn(
                DEFAULT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "  private Foo_Factory fooProvider;",
                "  private Provider<FooFactory> fooFactoryProvider;",
                "",
                "  @Override",
                "  public FooFactory fooFactory() {",
                "    return fooFactoryProvider.get();",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.fooProvider = Foo_Factory.create(Bar_Factory.create());",
                "    this.fooFactoryProvider = FooFactory_Impl.create(fooProvider);",
                "  }",
                "}")
            .build();

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, bar, fooFactory, component);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @Disabled
  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testFactoryGeneratorDuplicatedParamNames(CompilerMode compilerMode) {
    JavaFileObject componentSrc =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  @Component.Factory",
            "  interface Factory {",
            "    TestComponent create(@BindsInstance Bar arg);",
            "}",
            "  FooFactory getFooFactory();",
            "}");
    JavaFileObject factorySrc =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory {",
            "  Foo create(Integer arg);",
            "}");
    JavaFileObject barSrc =
        JavaFileObjects.forSourceLines("test.Bar", "package test;", "", "interface Bar {}");
    JavaFileObject injectSrc =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(Bar arg, @Assisted Integer argProvider) {}",
            "}");
    JavaFileObject generatedSrc =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines("package test;", "")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLinesIn(
                FAST_INIT_MODE,
                "public final class Foo_Factory {",
                "  private final Provider<Bar> argProvider;",
                "",
                "  public Foo_Factory(Provider<Bar> argProvider) {",
                "    this.argProvider = argProvider;",
                "  }",
                "",
                "  public Foo get(Integer argProvider2) {",
                "    return newInstance(argProvider.get(), argProvider2);",
                "  }",
                "",
                "  public static Foo_Factory create(Provider<Bar> argProvider) {",
                "    return new Foo_Factory(argProvider);",
                "  }",
                "",
                "  public static Foo newInstance(Object arg, Integer argProvider) {",
                "    return new Foo((Bar) arg, argProvider);",
                "  }",
                "}")
            .addLinesIn(
                DEFAULT_MODE,
                "public final class Foo_Factory {",
                "  private final Provider<Bar> argProvider;",
                "",
                "  public Foo_Factory(Provider<Bar> argProvider) {",
                "    this.argProvider = argProvider;",
                "  }",
                "",
                "  public Foo get(Integer argProvider2) {",
                "    return newInstance(argProvider.get(), argProvider2);",
                "  }",
                "",
                "  public static Foo_Factory create(Provider<Bar> argProvider) {",
                "    return new Foo_Factory(argProvider);",
                "  }",
                "",
                "  public static Foo newInstance(Object arg, Integer argProvider) {",
                "    return new Foo((Bar) arg, argProvider);",
                "  }",
                "}")
            .build();
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(componentSrc, factorySrc, barSrc, injectSrc);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.Foo_Factory")
        .containsLines(generatedSrc);
  }

  @Disabled
  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testParameterizedAssistParam(CompilerMode compilerMode) {
    JavaFileObject componentSrc =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory<String> getFooFactory();",
            "}");
    JavaFileObject factorySrc =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory<T> {",
            "  Foo<T> create(T arg);",
            "}");
    JavaFileObject injectSrc =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo<T> {",
            "  @AssistedInject",
            "  Foo(@Assisted T arg) {}",
            "}");
    JavaFileObject generatedSrc =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines("package test;", "")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLinesIn(
                FAST_INIT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "  private Provider<FooFactory<String>> fooFactoryProvider;",
                "  private final DaggerTestComponent testComponent = this;",
                "",
                "  @Override",
                "  public FooFactory<String> getFooFactory() {",
                "    return fooFactoryProvider.get();",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.fooFactoryProvider = SingleCheck.provider(new SwitchingProvider<FooFactory<String>>(testComponent, 0));",
                "  }",
                "",
                "  private static final class SwitchingProvider<T> implements Provider<T> {",
                "    private final DaggerTestComponent testComponent;",
                "    private final int id;",
                "",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0: // test.FooFactory<java.lang.String> ",
                "        return (T) new FooFactory<String>() {",
                "          @Override",
                "          public Foo<String> create(String arg) {",
                "            return new Foo<String>(arg);",
                "          }",
                "        };",
                "",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }",
                "  }",
                "}")
            .addLinesIn(
                DEFAULT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "  private Foo_Factory<String> fooProvider;",
                "  private Provider<FooFactory<String>> fooFactoryProvider;",
                "  private final DaggerTestComponent testComponent = this;",
                "",
                "  private DaggerTestComponent() {",
                "    initialize();",
                "  }",
                "",
                "  public static Builder builder() {",
                "    return new Builder();",
                "  }",
                "",
                "  public static TestComponent create() {",
                "    return new Builder().build();",
                "  }",
                "",
                "  @Override",
                "  public FooFactory<String> getFooFactory() {",
                "    return fooFactoryProvider.get();",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.fooProvider = Foo_Factory.create();",
                "    this.fooFactoryProvider = FooFactory_Impl.create(fooProvider);",
                "  }",
                "}")
            .build();
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentSrc, factorySrc, injectSrc);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedSrc);
  }
}
