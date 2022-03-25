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

import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.Compiler;
import io.jbock.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ComponentShardTest {
  private static final int BINDINGS_PER_SHARD = 2;

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testNewShardCreated(CompilerMode compilerMode) {
    // Add all bindings.
    //
    //     1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7
    //          ^--------/
    //
    List<JavaFileObject> javaFileObjects = new ArrayList<>();
    javaFileObjects
        // Shard 2: Bindings (1)
        .add(createBinding("Binding1", "Binding2 binding2"));
    // Shard 1: Bindings (2, 3, 4, 5). Contains more than 2 bindings due to cycle.
    javaFileObjects.add(createBinding("Binding2", "Binding3 binding3"));
    javaFileObjects.add(createBinding("Binding3", "Binding4 binding4"));
    javaFileObjects.add(
        createBinding("Binding4", "Binding5 binding5, Provider<Binding2> binding2Provider"));
    javaFileObjects.add(createBinding("Binding5", "Binding6 binding6"));
    // Component shard: Bindings (6, 7)
    javaFileObjects.add(createBinding("Binding6", "Binding7 binding7"));
    javaFileObjects.add(createBinding("Binding7"));

    // Add the component with entry points for each binding and its provider.
    javaFileObjects.add(
        JavaFileObjects.forSourceLines(
            "dagger.internal.codegen.TestComponent",
            "package dagger.internal.codegen;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface TestComponent {",
            "  Binding1 binding1();",
            "  Binding2 binding2();",
            "  Binding3 binding3();",
            "  Binding4 binding4();",
            "  Binding5 binding5();",
            "  Binding6 binding6();",
            "  Binding7 binding7();",
            "  Provider<Binding1> providerBinding1();",
            "  Provider<Binding2> providerBinding2();",
            "  Provider<Binding3> providerBinding3();",
            "  Provider<Binding4> providerBinding4();",
            "  Provider<Binding5> providerBinding5();",
            "  Provider<Binding6> providerBinding6();",
            "  Provider<Binding7> providerBinding7();",
            "}"));

    Compilation compilation = compiler(compilerMode).compile(javaFileObjects);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("dagger.internal.codegen.DaggerTestComponent")
        .containsLines(
            compilerMode
                .javaFileBuilder("dagger.internal.codegen.DaggerTestComponent")
                .addLines("package dagger.internal.codegen;", "")
                .addLines(GeneratedLines.generatedAnnotations())
                .addLines(
                    "final class DaggerTestComponent {",
                    "  private static final class TestComponentImplShard {",
                    "    private Provider<Binding5> binding5Provider;",
                    "    private Provider<Binding2> binding2Provider;",
                    "    private Provider<Binding4> binding4Provider;",
                    "    private Provider<Binding3> binding3Provider;",
                    "    private final TestComponentImpl testComponentImpl;",
                    "",
                    "    private TestComponentImplShard(TestComponentImpl testComponentImpl) {",
                    "      this.testComponentImpl = testComponentImpl;",
                    "      initialize();",
                    "    }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding5Provider = DoubleCheck.provider(Binding5_Factory.create(testComponentImpl.binding6Provider));",
                    "      this.binding2Provider = new DelegateFactory<>();",
                    "      this.binding4Provider = DoubleCheck.provider(Binding4_Factory.create(binding5Provider, binding2Provider));",
                    "      this.binding3Provider = DoubleCheck.provider(Binding3_Factory.create(binding4Provider));",
                    "      DelegateFactory.setDelegate(binding2Provider, DoubleCheck.provider(Binding2_Factory.create(binding3Provider)));",
                    "    }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding5Provider = DoubleCheck.provider(new TestComponentImpl.SwitchingProvider<Binding5>(testComponentImpl, 4));",
                    "      this.binding4Provider = DoubleCheck.provider(new TestComponentImpl.SwitchingProvider<Binding4>(testComponentImpl, 3));",
                    "      this.binding3Provider = DoubleCheck.provider(new TestComponentImpl.SwitchingProvider<Binding3>(testComponentImpl, 2));",
                    "      this.binding2Provider = DoubleCheck.provider(new TestComponentImpl.SwitchingProvider<Binding2>(testComponentImpl, 1));",
                    "    }")
                .addLines(
                    "  }",
                    "",
                    "  private static final class TestComponentImplShard2 {",
                    "    private Provider<Binding1> binding1Provider;",
                    "    private final TestComponentImpl testComponentImpl;",
                    "",
                    "    private TestComponentImplShard2(TestComponentImpl testComponentImpl) {",
                    "      this.testComponentImpl = testComponentImpl;",
                    "      initialize();",
                    "    }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding1Provider = DoubleCheck.provider(Binding1_Factory.create(testComponentImpl.testComponentImplShard.binding2Provider));",
                    "    }",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding1Provider = DoubleCheck.provider(new TestComponentImpl.SwitchingProvider<Binding1>(testComponentImpl, 0));",
                    "    }",
                    "  }")
                .addLines(
                    "  private static final class TestComponentImpl implements TestComponent {",
                    "    private Provider<Binding7> binding7Provider;",
                    "    private Provider<Binding6> binding6Provider;",
                    "    private TestComponentImplShard testComponentImplShard;",
                    "    private TestComponentImplShard2 testComponentImplShard2;",
                    "    private final TestComponentImpl testComponentImpl = this;",
                    "",
                    "    private TestComponentImpl() {",
                    "      initialize();",
                    "      testComponentImplShard = new TestComponentImplShard(testComponentImpl);",
                    "      testComponentImplShard2 = new TestComponentImplShard2(testComponentImpl);",
                    "    }")
                .addLines(
                    "    @Override",
                    "    public Binding1 binding1() {",
                    "      return testComponentImpl.testComponentImplShard2.binding1Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding2 binding2() {",
                    "      return testComponentImpl.testComponentImplShard.binding2Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding3 binding3() {",
                    "      return testComponentImpl.testComponentImplShard.binding3Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding4 binding4() {",
                    "      return testComponentImpl.testComponentImplShard.binding4Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding5 binding5() {",
                    "      return testComponentImpl.testComponentImplShard.binding5Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding6 binding6() {",
                    "      return binding6Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding7 binding7() {",
                    "      return binding7Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding1> providerBinding1() {",
                    "      return testComponentImpl.testComponentImplShard2.binding1Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding2> providerBinding2() {",
                    "      return testComponentImpl.testComponentImplShard.binding2Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding3> providerBinding3() {",
                    "      return testComponentImpl.testComponentImplShard.binding3Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding4> providerBinding4() {",
                    "      return testComponentImpl.testComponentImplShard.binding4Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding5> providerBinding5() {",
                    "      return testComponentImpl.testComponentImplShard.binding5Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding6> providerBinding6() {",
                    "      return binding6Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding7> providerBinding7() {",
                    "      return binding7Provider;",
                    "    }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding7Provider = DoubleCheck.provider(Binding7_Factory.create());",
                    "      this.binding6Provider = DoubleCheck.provider(Binding6_Factory.create(binding7Provider));",
                    "    }",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding7Provider = DoubleCheck.provider(new SwitchingProvider<Binding7>(testComponentImpl, 6));",
                    "      this.binding6Provider = DoubleCheck.provider(new SwitchingProvider<Binding6>(testComponentImpl, 5));",
                    "    }",
                    "",
                    "    private static final class SwitchingProvider<T> implements Provider<T> {",
                    "      @SuppressWarnings(\"unchecked\")",
                    "      @Override",
                    "      public T get() {",
                    "        switch (id) {",
                    "          case 0: // dagger.internal.codegen.Binding1 ", // 136, last match
                    "          return (T) new Binding1(testComponentImpl.testComponentImplShard.binding2Provider.get());",
                    "",
                    "          case 1: // dagger.internal.codegen.Binding2 ",
                    "          return (T) new Binding2(testComponentImpl.testComponentImplShard.binding3Provider.get());",
                    "",
                    "          case 2: // dagger.internal.codegen.Binding3 ",
                    "          return (T) new Binding3(testComponentImpl.testComponentImplShard.binding4Provider.get());",
                    "",
                    "          case 3: // dagger.internal.codegen.Binding4 ",
                    "          return (T) new Binding4(testComponentImpl.testComponentImplShard.binding5Provider.get(), testComponentImpl.testComponentImplShard.binding2Provider);",
                    "",
                    "          case 4: // dagger.internal.codegen.Binding5 ",
                    "          return (T) new Binding5(testComponentImpl.binding6Provider.get());",
                    "",
                    "          case 5: // dagger.internal.codegen.Binding6 ",
                    "          return (T) new Binding6(testComponentImpl.binding7Provider.get());",
                    "",
                    "          case 6: // dagger.internal.codegen.Binding7 ",
                    "          return (T) new Binding7();",
                    "",
                    "          default: throw new AssertionError(id);",
                    "        }",
                    "      }",
                    "    }")
                .build());
  }

  private static JavaFileObject createBinding(String bindingName, String... deps) {
    return JavaFileObjects.forSourceLines(
        "dagger.internal.codegen." + bindingName,
        "package dagger.internal.codegen;",
        "",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Provider;",
        "import jakarta.inject.Singleton;",
        "",
        "@Singleton",
        "final class " + bindingName + " {",
        "  @Inject",
        "  " + bindingName + "(" + String.join(", ", deps) + ") {}",
        "}");
  }

  private Compiler compiler(CompilerMode compilerMode) {
    Set<String> options = new LinkedHashSet<>();
    options.add("-Adagger.generatedClassExtendsComponent=DISABLED");
    options.add("-Adagger.keysPerComponentShard=" + BINDINGS_PER_SHARD);
    options.addAll(compilerMode.javacopts());
    return compilerWithOptions(options);
  }
}
