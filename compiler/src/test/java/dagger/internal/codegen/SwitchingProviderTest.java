/*
 * Copyright (C) 2014 The Dagger Authors.
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
import io.jbock.testing.compile.Compiler;
import io.jbock.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
class SwitchingProviderTest {

  @Test
  void switchingProviderTest() {
    List<JavaFileObject> javaFileObjects = new ArrayList<>();
    StringBuilder entryPoints = new StringBuilder();
    for (int i = 0; i <= 100; i++) {
      String bindingName = "Binding" + i;
      javaFileObjects.add(
          JavaFileObjects.forSourceLines(
              "test." + bindingName,
              "package test;",
              "",
              "import jakarta.inject.Inject;",
              "",
              "final class " + bindingName + " {",
              "  @Inject",
              "  " + bindingName + "() {}",
              "}"));
      entryPoints.append(String.format("  Provider<%1$s> get%1$sProvider();\n", bindingName));
    }

    javaFileObjects.add(
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "interface TestComponent {",
            entryPoints.toString(),
            "}"));

    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {",
        "  private static final class SwitchingProvider<T> implements Provider<T> {",
        "    @SuppressWarnings(\"unchecked\")",
        "    private T get0() {",
        "      switch (id) {",
        "        case 0: // test.Binding0 ",
        "        return (T) new Binding0();",
        "        case 1: // test.Binding1 ",
        "        return (T) new Binding1();",
        "        case 2: // test.Binding2 ",
        "        return (T) new Binding2();",
        "        case 3: // test.Binding3 ",
        "        return (T) new Binding3();",
        "        case 4: // test.Binding4 ",
        "        return (T) new Binding4();",
        "        case 5: // test.Binding5 ",
        "        return (T) new Binding5();",
        "        case 6: // test.Binding6 ",
        "        return (T) new Binding6();",
        "        case 7: // test.Binding7 ",
        "        return (T) new Binding7();",
        "        case 8: // test.Binding8 ",
        "        return (T) new Binding8();",
        "        case 9: // test.Binding9 ",
        "        return (T) new Binding9();",
        "        case 10: // test.Binding10 ",
        "        return (T) new Binding10();",
        "        case 11: // test.Binding11 ",
        "        return (T) new Binding11();",
        "        case 12: // test.Binding12 ",
        "        return (T) new Binding12();",
        "        case 13: // test.Binding13 ",
        "        return (T) new Binding13();",
        "        case 14: // test.Binding14 ",
        "        return (T) new Binding14();",
        "        case 15: // test.Binding15 ",
        "        return (T) new Binding15();",
        "        case 16: // test.Binding16 ",
        "        return (T) new Binding16();",
        "        case 17: // test.Binding17 ",
        "        return (T) new Binding17();",
        "        case 18: // test.Binding18 ",
        "        return (T) new Binding18();",
        "        case 19: // test.Binding19 ",
        "        return (T) new Binding19();",
        "        case 20: // test.Binding20 ",
        "        return (T) new Binding20();",
        "        case 21: // test.Binding21 ",
        "        return (T) new Binding21();",
        "        case 22: // test.Binding22 ",
        "        return (T) new Binding22();",
        "        case 23: // test.Binding23 ",
        "        return (T) new Binding23();",
        "        case 24: // test.Binding24 ",
        "        return (T) new Binding24();",
        "        case 25: // test.Binding25 ",
        "        return (T) new Binding25();",
        "        case 26: // test.Binding26 ",
        "        return (T) new Binding26();",
        "        case 27: // test.Binding27 ",
        "        return (T) new Binding27();",
        "        case 28: // test.Binding28 ",
        "        return (T) new Binding28();",
        "        case 29: // test.Binding29 ",
        "        return (T) new Binding29();",
        "        case 30: // test.Binding30 ",
        "        return (T) new Binding30();",
        "        case 31: // test.Binding31 ",
        "        return (T) new Binding31();",
        "        case 32: // test.Binding32 ",
        "        return (T) new Binding32();",
        "        case 33: // test.Binding33 ",
        "        return (T) new Binding33();",
        "        case 34: // test.Binding34 ",
        "        return (T) new Binding34();",
        "        case 35: // test.Binding35 ",
        "        return (T) new Binding35();",
        "        case 36: // test.Binding36 ",
        "        return (T) new Binding36();",
        "        case 37: // test.Binding37 ",
        "        return (T) new Binding37();",
        "        case 38: // test.Binding38 ",
        "        return (T) new Binding38();",
        "        case 39: // test.Binding39 ",
        "        return (T) new Binding39();",
        "        case 40: // test.Binding40 ",
        "        return (T) new Binding40();",
        "        case 41: // test.Binding41 ",
        "        return (T) new Binding41();",
        "        case 42: // test.Binding42 ",
        "        return (T) new Binding42();",
        "        case 43: // test.Binding43 ",
        "        return (T) new Binding43();",
        "        case 44: // test.Binding44 ",
        "        return (T) new Binding44();",
        "        case 45: // test.Binding45 ",
        "        return (T) new Binding45();",
        "        case 46: // test.Binding46 ",
        "        return (T) new Binding46();",
        "        case 47: // test.Binding47 ",
        "        return (T) new Binding47();",
        "        case 48: // test.Binding48 ",
        "        return (T) new Binding48();",
        "        case 49: // test.Binding49 ",
        "        return (T) new Binding49();",
        "        case 50: // test.Binding50 ",
        "        return (T) new Binding50();",
        "        case 51: // test.Binding51 ",
        "        return (T) new Binding51();",
        "        case 52: // test.Binding52 ",
        "        return (T) new Binding52();",
        "        case 53: // test.Binding53 ",
        "        return (T) new Binding53();",
        "        case 54: // test.Binding54 ",
        "        return (T) new Binding54();",
        "        case 55: // test.Binding55 ",
        "        return (T) new Binding55();",
        "        case 56: // test.Binding56 ",
        "        return (T) new Binding56();",
        "        case 57: // test.Binding57 ",
        "        return (T) new Binding57();",
        "        case 58: // test.Binding58 ",
        "        return (T) new Binding58();",
        "        case 59: // test.Binding59 ",
        "        return (T) new Binding59();",
        "        case 60: // test.Binding60 ",
        "        return (T) new Binding60();",
        "        case 61: // test.Binding61 ",
        "        return (T) new Binding61();",
        "        case 62: // test.Binding62 ",
        "        return (T) new Binding62();",
        "        case 63: // test.Binding63 ",
        "        return (T) new Binding63();",
        "        case 64: // test.Binding64 ",
        "        return (T) new Binding64();",
        "        case 65: // test.Binding65 ",
        "        return (T) new Binding65();",
        "        case 66: // test.Binding66 ",
        "        return (T) new Binding66();",
        "        case 67: // test.Binding67 ",
        "        return (T) new Binding67();",
        "        case 68: // test.Binding68 ",
        "        return (T) new Binding68();",
        "        case 69: // test.Binding69 ",
        "        return (T) new Binding69();",
        "        case 70: // test.Binding70 ",
        "        return (T) new Binding70();",
        "        case 71: // test.Binding71 ",
        "        return (T) new Binding71();",
        "        case 72: // test.Binding72 ",
        "        return (T) new Binding72();",
        "        case 73: // test.Binding73 ",
        "        return (T) new Binding73();",
        "        case 74: // test.Binding74 ",
        "        return (T) new Binding74();",
        "        case 75: // test.Binding75 ",
        "        return (T) new Binding75();",
        "        case 76: // test.Binding76 ",
        "        return (T) new Binding76();",
        "        case 77: // test.Binding77 ",
        "        return (T) new Binding77();",
        "        case 78: // test.Binding78 ",
        "        return (T) new Binding78();",
        "        case 79: // test.Binding79 ",
        "        return (T) new Binding79();",
        "        case 80: // test.Binding80 ",
        "        return (T) new Binding80();",
        "        case 81: // test.Binding81 ",
        "        return (T) new Binding81();",
        "        case 82: // test.Binding82 ",
        "        return (T) new Binding82();",
        "        case 83: // test.Binding83 ",
        "        return (T) new Binding83();",
        "        case 84: // test.Binding84 ",
        "        return (T) new Binding84();",
        "        case 85: // test.Binding85 ",
        "        return (T) new Binding85();",
        "        case 86: // test.Binding86 ",
        "        return (T) new Binding86();",
        "        case 87: // test.Binding87 ",
        "        return (T) new Binding87();",
        "        case 88: // test.Binding88 ",
        "        return (T) new Binding88();",
        "        case 89: // test.Binding89 ",
        "        return (T) new Binding89();",
        "        case 90: // test.Binding90 ",
        "        return (T) new Binding90();",
        "        case 91: // test.Binding91 ",
        "        return (T) new Binding91();",
        "        case 92: // test.Binding92 ",
        "        return (T) new Binding92();",
        "        case 93: // test.Binding93 ",
        "        return (T) new Binding93();",
        "        case 94: // test.Binding94 ",
        "        return (T) new Binding94();",
        "        case 95: // test.Binding95 ",
        "        return (T) new Binding95();",
        "        case 96: // test.Binding96 ",
        "        return (T) new Binding96();",
        "        case 97: // test.Binding97 ",
        "        return (T) new Binding97();",
        "        case 98: // test.Binding98 ",
        "        return (T) new Binding98();",
        "        case 99: // test.Binding99 ",
        "        return (T) new Binding99();",
        "        default: throw new AssertionError(id);",
        "      }",
        "    }",
        "",
        "    @SuppressWarnings(\"unchecked\")",
        "    private T get1() {",
        "      switch (id) {",
        "        case 100: // test.Binding100 ",
        "        return (T) new Binding100();",
        "        default: throw new AssertionError(id);",
        "      }",
        "    }",
        "",
        "    @Override",
        "    public T get() {",
        "      switch (id / 100) {",
        "        case 0: return get0();",
        "        case 1: return get1();",
        "        default: throw new AssertionError(id);",
        "      }",
        "    }",
        "  }",
        "}");

    Compilation compilation = compilerWithAndroidMode().compile(javaFileObjects);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @Test
  void unscopedBinds() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String s() {",
            "    return new String();",
            "  }",
            "",
            "  @Binds CharSequence c(String s);",
            "  @Binds Object o(CharSequence c);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<Object> objectProvider();",
            "  Provider<CharSequence> charSequenceProvider();",
            "}");

    Compilation compilation = compilerWithAndroidMode().compile(module, component);
    assertThat(compilation).succeeded();
    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {",
        "  private Provider<String> sProvider;",
        "",
        "  @Override",
        "  public Provider<Object> objectProvider() {",
        "    return ((Provider) sProvider);",
        "  }",
        "",
        "  @Override",
        "  public Provider<CharSequence> charSequenceProvider() {",
        "    return ((Provider) sProvider);",
        "  }",
        "",
        "  @SuppressWarnings(\"unchecked\")",
        "  private void initialize() {",
        "    this.sProvider = new SwitchingProvider<>(testComponent, 0);",
        "  }",
        "",
        "  private static final class SwitchingProvider<T> implements Provider<T> {",
        "    @SuppressWarnings(\"unchecked\")",
        "    @Override",
        "    public T get() {",
        "      switch (id) {",
        "        case 0: // java.lang.String ",
        "        return (T) TestModule_SFactory.s();",
        "        default: throw new AssertionError(id);",
        "      }",
        "    }",
        "  }",
        "}");
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @Test
  void scopedBinds() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import jakarta.inject.Singleton;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String s() {",
            "    return new String();",
            "  }",
            "",
            "  @Binds @Singleton Object o(CharSequence s);",
            "  @Binds @Singleton CharSequence c(String s);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<Object> objectProvider();",
            "  Provider<CharSequence> charSequenceProvider();",
            "}");

    Compilation compilation = compilerWithAndroidMode().compile(module, component);
    assertThat(compilation).succeeded();
    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {",
        "  private Provider<String> sProvider;",
        "  private Provider<CharSequence> cProvider;",
        "",
        "  @Override",
        "  public Provider<Object> objectProvider() {",
        "    return ((Provider) cProvider);",
        "  }",
        "",
        "  @Override",
        "  public Provider<CharSequence> charSequenceProvider() {",
        "    return cProvider;",
        "  }",
        "",
        "  @SuppressWarnings(\"unchecked\")",
        "  private void initialize() {",
        "    this.sProvider = new SwitchingProvider<>(testComponent, 0);",
        "    this.cProvider = DoubleCheck.provider((Provider) sProvider);",
        "  }",
        "",
        "  private static final class SwitchingProvider<T> implements Provider<T> {",
        "    @SuppressWarnings(\"unchecked\")",
        "    @Override",
        "    public T get() {",
        "      switch (id) {",
        "        case 0: // java.lang.String ",
        "        return (T) TestModule_SFactory.s();",
        "        default: throw new AssertionError(id);",
        "      }",
        "    }",
        "  }",
        "}");
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  private Compiler compilerWithAndroidMode() {
    return compilerWithOptions(CompilerMode.FAST_INIT_MODE.javacopts());
  }
}
