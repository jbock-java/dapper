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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

public class NullableBindingValidationTest {
  private static final JavaFileObject NULLABLE =
      JavaFileObjects.forSourceLines(
          "test.Nullable", // force one-string-per-line format
          "package test;",
          "",
          "public @interface Nullable {}");

  @Test
  public void nullCheckForOptionalProvider() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import java.util.Optional;",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Provider;",
            "",
            "final class A {",
            "  @Inject A(Optional<Provider<String>> optional) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Provides;",
            "import jakarta.inject.Inject;",
            "",
            "@dagger.Module",
            "abstract class TestModule {",
            "  @Nullable @Provides static String provideString() { return null; }",
            "  @BindsOptionalOf abstract String optionalString();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).succeeded();
  }

  @Test
  public void nullCheckForOptionalLazy() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import java.util.Optional;",
            "import dagger.Lazy;",
            "import jakarta.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(Optional<Lazy<String>> optional) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Provides;",
            "import jakarta.inject.Inject;",
            "",
            "@dagger.Module",
            "abstract class TestModule {",
            "  @Nullable @Provides static String provideString() { return null; }",
            "  @BindsOptionalOf abstract String optionalString();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).succeeded();
  }

  @Test
  public void nullCheckForOptionalProviderOfLazy() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import java.util.Optional;",
            "import dagger.Lazy;",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Provider;",
            "",
            "final class A {",
            "  @Inject A(Optional<Provider<Lazy<String>>> optional) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Provides;",
            "import jakarta.inject.Inject;",
            "",
            "@dagger.Module",
            "abstract class TestModule {",
            "  @Nullable @Provides static String provideString() { return null; }",
            "  @BindsOptionalOf abstract String optionalString();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).succeeded();
  }
}
