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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ComponentShardTest {
  private static final int BINDINGS_PER_SHARD = 2;

  @Parameters(name = "{0}")
  public static ImmutableCollection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ComponentShardTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void testNewShardCreated() {
    // Add all bindings.
    //
    //     1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7
    //          ^--------/
    //
    ImmutableList.Builder<JavaFileObject> javaFileObjects = ImmutableList.builder();
    javaFileObjects
        // Shard 2: Bindings (1)
        .add(createBinding("Binding1", "Binding2 binding2"))
        // Shard 1: Bindings (2, 3, 4, 5). Contains more than 2 bindings due to cycle.
        .add(createBinding("Binding2", "Binding3 binding3"))
        .add(createBinding("Binding3", "Binding4 binding4"))
        .add(createBinding("Binding4", "Binding5 binding5, Provider<Binding2> binding2Provider"))
        .add(createBinding("Binding5", "Binding6 binding6"))
        // Component shard: Bindings (6, 7)
        .add(createBinding("Binding6", "Binding7 binding7"))
        .add(createBinding("Binding7"));

    // Add the component with entry points for each binding and its provider.
    javaFileObjects.add(
        JavaFileObjects.forSourceLines(
            "dagger.internal.codegen.TestComponent",
            "package dagger.internal.codegen;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "import javax.inject.Singleton;",
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

    Compilation compilation = compiler().compile(javaFileObjects.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("dagger.internal.codegen.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("dagger.internal.codegen.DaggerTestComponent")
                .addLines(
                    "package dagger.internal.codegen;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent implements TestComponent {")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  private Shard1 shard1;",
                    "  private Shard2 shard2;",
                    "  private final DaggerTestComponent testComponent = this;",
                    "  private volatile Object binding6 = new MemoizedSentinel();",
                    "  private volatile Object binding7 = new MemoizedSentinel();",
                    "  private volatile Provider<Binding6> binding6Provider;",
                    "  private volatile Provider<Binding7> binding7Provider;",
                    "",
                    "  private DaggerTestComponent() {",
                    "    shard1 = new Shard1();",
                    "    shard2 = new Shard2();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding1 binding1() {",
                    "    return testComponent.shard2.binding1();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding2 binding2() {",
                    "    return testComponent.shard1.binding2();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding3 binding3() {",
                    "    return testComponent.shard1.binding3();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding4 binding4() {",
                    "    return testComponent.shard1.binding4();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding5 binding5() {",
                    "    return testComponent.shard1.binding5();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding6 binding6() {",
                    "    Object local = binding6;",
                    "    if (local instanceof MemoizedSentinel) {",
                    "      synchronized (local) {",
                    "        local = binding6;",
                    "        if (local instanceof MemoizedSentinel) {",
                    "          local = new Binding6(binding7());",
                    "          binding6 = DoubleCheck.reentrantCheck(binding6, local);",
                    "        }",
                    "      }",
                    "    }",
                    "    return (Binding6) local;",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding7 binding7() {",
                    "    Object local = binding7;",
                    "    if (local instanceof MemoizedSentinel) {",
                    "      synchronized (local) {",
                    "        local = binding7;",
                    "        if (local instanceof MemoizedSentinel) {",
                    "          local = new Binding7();",
                    "          binding7 = DoubleCheck.reentrantCheck(binding7, local);",
                    "        }",
                    "      }",
                    "    }",
                    "    return (Binding7) local;",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding1> providerBinding1() {",
                    "    return testComponent.shard2.binding1Provider();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding2> providerBinding2() {",
                    "    return testComponent.shard1.binding2Provider();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding3> providerBinding3() {",
                    "    return testComponent.shard1.binding3Provider();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding4> providerBinding4() {",
                    "    return testComponent.shard1.binding4Provider();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding5> providerBinding5() {",
                    "    return testComponent.shard1.binding5Provider();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding6> providerBinding6() {",
                    "    Object local = binding6Provider;",
                    "    if (local == null) {",
                    "      local = new SwitchingProvider<>(testComponent, 5);",
                    "      binding6Provider = (Provider<Binding6>) local;",
                    "    }",
                    "    return (Provider<Binding6>) local;",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding7> providerBinding7() {",
                    "    Object local = binding7Provider;",
                    "    if (local == null) {",
                    "      local = new SwitchingProvider<>(testComponent, 6);",
                    "      binding7Provider = (Provider<Binding7>) local;",
                    "    }",
                    "    return (Provider<Binding7>) local;",
                    "  }",
                    "",
                    "  private final class Shard1 {",
                    "    private volatile Object binding5 = new MemoizedSentinel();",
                    "    private volatile Object binding4 = new MemoizedSentinel();",
                    "    private volatile Object binding3 = new MemoizedSentinel();",
                    "    private volatile Object binding2 = new MemoizedSentinel();",
                    "    private volatile Provider<Binding2> binding2Provider;",
                    "    private volatile Provider<Binding3> binding3Provider;",
                    "    private volatile Provider<Binding4> binding4Provider;",
                    "    private volatile Provider<Binding5> binding5Provider;",
                    "",
                    "    private Shard1() {}",
                    "",
                    "    private Binding5 binding5() {",
                    "      Object local = binding5;",
                    "      if (local instanceof MemoizedSentinel) {",
                    "        synchronized (local) {",
                    "          local = binding5;",
                    "          if (local instanceof MemoizedSentinel) {",
                    "            local = new Binding5(testComponent.binding6());",
                    "            binding5 = DoubleCheck.reentrantCheck(binding5, local);",
                    "          }",
                    "        }",
                    "      }",
                    "      return (Binding5) local;",
                    "    }",
                    "",
                    "    private Provider<Binding2> binding2Provider() {",
                    "      Object local = binding2Provider;",
                    "      if (local == null) {",
                    "        local = new SwitchingProvider<>(testComponent, 0);",
                    "        binding2Provider = (Provider<Binding2>) local;",
                    "      }",
                    "      return (Provider<Binding2>) local;",
                    "    }",
                    "",
                    "    private Binding4 binding4() {",
                    "      Object local = binding4;",
                    "      if (local instanceof MemoizedSentinel) {",
                    "        synchronized (local) {",
                    "          local = binding4;",
                    "          if (local instanceof MemoizedSentinel) {",
                    "            local = new Binding4(binding5(), binding2Provider());",
                    "            binding4 = DoubleCheck.reentrantCheck(binding4, local);",
                    "          }",
                    "        }",
                    "      }",
                    "      return (Binding4) local;",
                    "    }",
                    "",
                    "    private Binding3 binding3() {",
                    "      Object local = binding3;",
                    "      if (local instanceof MemoizedSentinel) {",
                    "        synchronized (local) {",
                    "          local = binding3;",
                    "          if (local instanceof MemoizedSentinel) {",
                    "            local = new Binding3(binding4());",
                    "            binding3 = DoubleCheck.reentrantCheck(binding3, local);",
                    "          }",
                    "        }",
                    "      }",
                    "      return (Binding3) local;",
                    "    }",
                    "",
                    "    private Binding2 binding2() {",
                    "      Object local = binding2;",
                    "      if (local instanceof MemoizedSentinel) {",
                    "        synchronized (local) {",
                    "          local = binding2;",
                    "          if (local instanceof MemoizedSentinel) {",
                    "            local = new Binding2(binding3());",
                    "            binding2 = DoubleCheck.reentrantCheck(binding2, local);",
                    "          }",
                    "        }",
                    "      }",
                    "      return (Binding2) local;",
                    "    }",
                    "",
                    "    private Provider<Binding3> binding3Provider() {",
                    "      Object local = binding3Provider;",
                    "      if (local == null) {",
                    "        local = new SwitchingProvider<>(testComponent, 2);",
                    "        binding3Provider = (Provider<Binding3>) local;",
                    "      }",
                    "      return (Provider<Binding3>) local;",
                    "    }",
                    "",
                    "    private Provider<Binding4> binding4Provider() {",
                    "      Object local = binding4Provider;",
                    "      if (local == null) {",
                    "        local = new SwitchingProvider<>(testComponent, 3);",
                    "        binding4Provider = (Provider<Binding4>) local;",
                    "      }",
                    "      return (Provider<Binding4>) local;",
                    "    }",
                    "",
                    "    private Provider<Binding5> binding5Provider() {",
                    "      Object local = binding5Provider;",
                    "      if (local == null) {",
                    "        local = new SwitchingProvider<>(testComponent, 4);",
                    "        binding5Provider = (Provider<Binding5>) local;",
                    "      }",
                    "      return (Provider<Binding5>) local;",
                    "    }",
                    "  }",
                    "",
                    "  private final class Shard2 {",
                    "    private volatile Object binding1 = new MemoizedSentinel();",
                    "    private volatile Provider<Binding1> binding1Provider;",
                    "",
                    "    private Shard2() {}",
                    "",
                    "    private Binding1 binding1() {",
                    "      Object local = binding1;",
                    "      if (local instanceof MemoizedSentinel) {",
                    "        synchronized (local) {",
                    "          local = binding1;",
                    "          if (local instanceof MemoizedSentinel) {",
                    "            local = new Binding1(testComponent.shard1.binding2());",
                    "            binding1 = DoubleCheck.reentrantCheck(binding1, local);",
                    "          }",
                    "        }",
                    "      }",
                    "      return (Binding1) local;",
                    "    }",
                    "",
                    "    private Provider<Binding1> binding1Provider() {",
                    "      Object local = binding1Provider;",
                    "      if (local == null) {",
                    "        local = new SwitchingProvider<>(testComponent, 1);",
                    "        binding1Provider = (Provider<Binding1>) local;",
                    "      }",
                    "      return (Provider<Binding1>) local;",
                    "    }",
                    "  }",
                    "",
                    "  private static final class SwitchingProvider<T> implements Provider<T> {",
                    "    @SuppressWarnings(\"unchecked\")",
                    "    @Override",
                    "    public T get() {",
                    "      switch (id) {",
                    "        case 0: // dagger.internal.codegen.Binding2 ",
                    "        return (T) testComponent.shard1.binding2();",
                    "",
                    "        case 1: // dagger.internal.codegen.Binding1 ",
                    "        return (T) testComponent.shard2.binding1();",
                    "",
                    "        case 2: // dagger.internal.codegen.Binding3 ",
                    "        return (T) testComponent.shard1.binding3();",
                    "",
                    "        case 3: // dagger.internal.codegen.Binding4 ",
                    "        return (T) testComponent.shard1.binding4();",
                    "",
                    "        case 4: // dagger.internal.codegen.Binding5 ",
                    "        return (T) testComponent.shard1.binding5();",
                    "",
                    "        case 5: // dagger.internal.codegen.Binding6 ",
                    "        return (T) testComponent.binding6();",
                    "",
                    "        case 6: // dagger.internal.codegen.Binding7 ",
                    "        return (T) testComponent.binding7();",
                    "",
                    "        default: throw new AssertionError(id);",
                    "      }",
                    "    }",
                    "  }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  private Shard1 shard1;",
                    "  private Shard2 shard2;",
                    "  private final DaggerTestComponent testComponent = this;",
                    "  private Provider<Binding7> binding7Provider;",
                    "  private Provider<Binding6> binding6Provider;",
                    "",
                    "  private DaggerTestComponent() {",
                    "    initialize();",
                    "    shard1 = new Shard1();",
                    "    shard2 = new Shard2();",
                    "  }",
                    "",
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "    this.binding7Provider =",
                    "        DoubleCheck.provider(Binding7_Factory.create());",
                    "    this.binding6Provider =",
                    "        DoubleCheck.provider(Binding6_Factory.create(binding7Provider));",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding1 binding1() {",
                    "    return testComponent.shard2.binding1Provider.get();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding2 binding2() {",
                    "    return testComponent.shard1.binding2Provider.get();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding3 binding3() {",
                    "    return testComponent.shard1.binding3Provider.get();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding4 binding4() {",
                    "    return testComponent.shard1.binding4Provider.get();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding5 binding5() {",
                    "    return testComponent.shard1.binding5Provider.get();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding6 binding6() {",
                    "    return binding6Provider.get();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Binding7 binding7() {",
                    "    return binding7Provider.get();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding1> providerBinding1() {",
                    "    return testComponent.shard2.binding1Provider;",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding2> providerBinding2() {",
                    "    return testComponent.shard1.binding2Provider;",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding3> providerBinding3() {",
                    "    return testComponent.shard1.binding3Provider;",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding4> providerBinding4() {",
                    "    return testComponent.shard1.binding4Provider;",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding5> providerBinding5() {",
                    "    return testComponent.shard1.binding5Provider;",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding6> providerBinding6() {",
                    "    return binding6Provider;",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Binding7> providerBinding7() {",
                    "    return binding7Provider;",
                    "  }",
                    "",
                    "  private final class Shard1 {",
                    "    private Provider<Binding5> binding5Provider;",
                    "    private Provider<Binding2> binding2Provider;",
                    "    private Provider<Binding4> binding4Provider;",
                    "    private Provider<Binding3> binding3Provider;",
                    "",
                    "    private Shard1() {",
                    "      initialize();",
                    "    }",
                    "",
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding5Provider =",
                    "          DoubleCheck.provider(",
                    "              Binding5_Factory.create(testComponent.binding6Provider));",
                    "      this.binding2Provider = new DelegateFactory<>();",
                    "      this.binding4Provider =",
                    "          DoubleCheck.provider(",
                    "              Binding4_Factory.create(binding5Provider, binding2Provider));",
                    "      this.binding3Provider =",
                    "          DoubleCheck.provider(Binding3_Factory.create(binding4Provider));",
                    "      DelegateFactory.setDelegate(",
                    "          binding2Provider,",
                    "          DoubleCheck.provider(Binding2_Factory.create(binding3Provider)));",
                    "    }",
                    "  }",
                    "",
                    "  private final class Shard2 {",
                    "    private Provider<Binding1> binding1Provider;",
                    "",
                    "    private Shard2() {",
                    "      initialize();",
                    "    }",
                    "",
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding1Provider =",
                    "          DoubleCheck.provider(",
                    "              Binding1_Factory.create(",
                    "                  testComponent.shard1.binding2Provider));",
                    "    }",
                    "  }")
                .build());
  }

  private static JavaFileObject createBinding(String bindingName, String... deps) {
    return JavaFileObjects.forSourceLines(
        "dagger.internal.codegen." + bindingName,
        "package dagger.internal.codegen;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "import javax.inject.Singleton;",
        "",
        "@Singleton",
        "final class " + bindingName + " {",
        "  @Inject",
        "  " + bindingName + "(" + Arrays.stream(deps).collect(joining(", ")) + ") {}",
        "}");
  }

  private Compiler compiler() {
    return compilerWithOptions(
        ImmutableSet.<String>builder()
            .add("-Adagger.keysPerComponentShard=" + BINDINGS_PER_SHARD)
            .addAll(compilerMode.javacopts())
            .build());
  }
}
