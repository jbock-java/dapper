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

package dagger.internal.codegen;

import static io.jbock.testing.compile.CompilationSubject.assertThat;

import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class MembersInjectionTest {

  private final CompilerMode compilerMode = CompilerMode.DEFAULT_MODE;

  @Test
  void parentClass_injectedMembersInSupertype() {
    JavaFileObject childFile = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "public final class Child extends Parent {",
        "  @Inject Child() {}",
        "}");
    JavaFileObject parentFile = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "public abstract class Parent {",
        "  @Inject Dep dep;",
        "}");
    JavaFileObject depFile = JavaFileObjects.forSourceLines("test.Dep",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class Dep {",
        "  @Inject Dep() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  Child child();",
        "}");
    JavaFileObject generatedComponent =
        compilerMode.javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;")
            .addLines(
                GeneratedLines.generatedAnnotations())
            .addLines("final class DaggerTestComponent implements TestComponent {",
                "  private Child injectChild(Child instance) {",
                "    Parent_MembersInjector.injectDep(instance, new Dep());",
                "    return instance;",
                "  }",
                "",
                "  @Override",
                "  public Child child() {",
                "    return injectChild(Child_Factory.newInstance());",
                "  }",
                "}")
            .build();
    Compilation compilation =
        Compilers.compilerWithOptions(compilerMode.javacopts())
            .compile(childFile, parentFile, depFile, componentFile);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @Test
  void fieldInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.FieldInjection",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Provider;",
        "",
        "class FieldInjection {",
        "  @Inject String string;",
        "  @Inject Lazy<String> lazyString;",
        "  @Inject Provider<String> stringProvider;",
        "}");
    JavaFileObject expected =
        compilerMode.javaFileBuilder("test.FieldInjection_MembersInjector")
            .addLines(
                "package test;")
            .addLines(GeneratedLines.generatedImports(
                "import dagger.Lazy;",
                "import dagger.MembersInjector;",
                "import dagger.internal.DoubleCheck;",
                "import dagger.internal.InjectedFieldSignature;",
                "import jakarta.inject.Provider;"))
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines("public final class FieldInjection_MembersInjector implements MembersInjector<FieldInjection> {",
                "  private final Provider<String> stringProvider;",
                "  private final Provider<String> stringProvider2;",
                "  private final Provider<String> stringProvider3;",
                "",
                "  public FieldInjection_MembersInjector(Provider<String> stringProvider,",
                "      Provider<String> stringProvider2, Provider<String> stringProvider3) {",
                "    this.stringProvider = stringProvider;",
                "    this.stringProvider2 = stringProvider2;",
                "    this.stringProvider3 = stringProvider3;",
                "  }",
                "",
                "  public static MembersInjector<FieldInjection> create(Provider<String> stringProvider,",
                "      Provider<String> stringProvider2, Provider<String> stringProvider3) {",
                "    return new FieldInjection_MembersInjector(stringProvider, stringProvider2, stringProvider3);",
                "  }",
                "",
                "  @Override",
                "  public void injectMembers(FieldInjection instance) {",
                "    injectString(instance, stringProvider.get());",
                "    injectLazyString(instance, DoubleCheck.lazy(stringProvider2));",
                "    injectStringProvider(instance, stringProvider3);",
                "  }",
                "",
                "  @InjectedFieldSignature(\"test.FieldInjection.string\")",
                "  public static void injectString(Object instance, String string) {",
                "    ((FieldInjection) instance).string = string;",
                "  }",
                "",
                "  @InjectedFieldSignature(\"test.FieldInjection.lazyString\")",
                "  public static void injectLazyString(Object instance, Lazy<String> lazyString) {",
                "    ((FieldInjection) instance).lazyString = lazyString;",
                "  }",
                "",
                "  @InjectedFieldSignature(\"test.FieldInjection.stringProvider\")",
                "  public static void injectStringProvider(Object instance, Provider<String> stringProvider) {",
                "    ((FieldInjection) instance).stringProvider = stringProvider;",
                "  }",
                "}").build();

    Compilation compilation =
        Compilers.compilerWithOptions(compilerMode.javacopts())
            .compile(file);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.FieldInjection_MembersInjector")
        .containsLines(expected);
  }

  @Test
  void methodInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MethodInjection",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Provider;",
        "",
        "class MethodInjection {",
        "  @Inject void noArgs() {}",
        "  @Inject void oneArg(String string) {}",
        "  @Inject void manyArgs(",
        "      String string, Lazy<String> lazyString, Provider<String> stringProvider) {}",
        "}");
    JavaFileObject expected =
        compilerMode.javaFileBuilder("test.MethodInjection_MembersInjector")
            .addLines(
                "package test;",
                "")
            .addLines(GeneratedLines.generatedImports(
                "import dagger.Lazy;",
                "import dagger.MembersInjector;",
                "import dagger.internal.DoubleCheck;",
                "import jakarta.inject.Provider;"))
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines(
                "public final class MethodInjection_MembersInjector implements MembersInjector<MethodInjection> {",
                "  private final Provider<String> stringProvider;",
                "  private final Provider<String> stringProvider2;",
                "  private final Provider<String> stringProvider3;",
                "  private final Provider<String> stringProvider4;",
                "",
                "  public MethodInjection_MembersInjector(Provider<String> stringProvider,",
                "      Provider<String> stringProvider2, Provider<String> stringProvider3,",
                "      Provider<String> stringProvider4) {",
                "    this.stringProvider = stringProvider;",
                "    this.stringProvider2 = stringProvider2;",
                "    this.stringProvider3 = stringProvider3;",
                "    this.stringProvider4 = stringProvider4;",
                "  }",
                "",
                "  public static MembersInjector<MethodInjection> create(Provider<String> stringProvider,",
                "      Provider<String> stringProvider2, Provider<String> stringProvider3,",
                "      Provider<String> stringProvider4) {",
                "    return new MethodInjection_MembersInjector(stringProvider, stringProvider2, stringProvider3, stringProvider4);",
                "",
                "  @Override",
                "  public void injectMembers(MethodInjection instance) {",
                "    injectNoArgs(instance);",
                "    injectOneArg(instance, stringProvider.get());",
                "    injectManyArgs(instance, stringProvider2.get(), DoubleCheck.lazy(stringProvider3), stringProvider4);",
                "  }",
                "",
                "  public static void injectNoArgs(Object instance) {",
                "    ((MethodInjection) instance).noArgs();",
                "  }",
                "",
                "  public static void injectOneArg(Object instance, String string) {",
                "    ((MethodInjection) instance).oneArg(string);",
                "  }",
                "",
                "  public static void injectManyArgs(Object instance, String string, Lazy<String> lazyString,",
                "      Provider<String> stringProvider) {",
                "    ((MethodInjection) instance).manyArgs(string, lazyString, stringProvider);",
                "  }",
                "}").build();

    Compilation compilation =
        Compilers.compilerWithOptions(compilerMode.javacopts())
            .compile(file);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.MethodInjection_MembersInjector")
        .containsLines(expected);
  }
}
