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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.compilerWithOptions;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OptionalBindingRequestFulfillmentTest {

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void inlinedOptionalBindings(CompilerMode compilerMode) {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.BindsOptionalOf;",
            "import other.Maybe;",
            "import other.DefinitelyNot;",
            "",
            "@Module",
            "interface TestModule {",
            "  @BindsOptionalOf Maybe maybe();",
            "  @BindsOptionalOf DefinitelyNot definitelyNot();",
            "}");
    JavaFileObject maybe =
        JavaFileObjects.forSourceLines(
            "other.Maybe",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "public class Maybe {",
            "  @Module",
            "  public static class MaybeModule {",
            "    @Provides static Maybe provideMaybe() { return new Maybe(); }",
            "  }",
            "}");
    JavaFileObject definitelyNot =
        JavaFileObjects.forSourceLines(
            "other.DefinitelyNot",
            "package other;",
            "",
            "public class DefinitelyNot {}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import java.util.Optional;",
            "import dagger.Component;",
            "import dagger.Lazy;",
            "import jakarta.inject.Provider;",
            "import other.Maybe;",
            "import other.DefinitelyNot;",
            "",
            "@Component(modules = {TestModule.class, Maybe.MaybeModule.class})",
            "interface TestComponent {",
            "  Optional<Maybe> maybe();",
            "  Optional<Provider<Lazy<Maybe>>> providerOfLazyOfMaybe();",
            "  Optional<DefinitelyNot> definitelyNot();",
            "  Optional<Provider<Lazy<DefinitelyNot>>> providerOfLazyOfDefinitelyNot();",
            "}");

    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;",
        "import java.util.Optional;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {");
    if (compilerMode == FAST_INIT_MODE) {
      Collections.addAll(generatedComponent,
          "  private volatile Provider<Maybe> provideMaybeProvider;",
          "",
          "  private Provider<Maybe> maybeProvider() {",
          "    Object local = provideMaybeProvider;",
          "    if (local == null) {",
          "      local = new SwitchingProvider<>(testComponent, 0);",
          "      provideMaybeProvider = (Provider<Maybe>) local;",
          "    }",
          "    return (Provider<Maybe>) local;",
          "  }");
    }
    Collections.addAll(generatedComponent,
        "  @Override",
        "  public Optional<Maybe> maybe() {",
        "    return Optional.of(Maybe_MaybeModule_ProvideMaybeFactory.provideMaybe());",
        "  }",
        "",
        "  @Override",
        "  public Optional<Provider<Lazy<Maybe>>> providerOfLazyOfMaybe() {");
    if (compilerMode == FAST_INIT_MODE) {
      Collections.addAll(generatedComponent,
          "    return Optional.of(ProviderOfLazy.create(maybeProvider()));");
    } else {
      Collections.addAll(generatedComponent,
          "    return Optional.of(ProviderOfLazy.create(Maybe_MaybeModule_ProvideMaybeFactory.create()));");
    }
    Collections.addAll(generatedComponent,
        "  }",
        "",
        "  @Override",
        "  public Optional<DefinitelyNot> definitelyNot() {",
        "    return Optional.empty();",
        "  }",
        "",
        "  @Override",
        "  public Optional<Provider<Lazy<DefinitelyNot>>> providerOfLazyOfDefinitelyNot() {",
        "    return Optional.empty();",
        "  }");
    if (compilerMode == FAST_INIT_MODE) {
      Collections.addAll(generatedComponent,
          "  private static final class SwitchingProvider<T> implements Provider<T> {",
          "    @SuppressWarnings(\"unchecked\")",
          "    @Override",
          "    public T get() {",
          "      switch (id) {",
          "        case 0: // other.Maybe ",
          "        return (T) Maybe_MaybeModule_ProvideMaybeFactory.provideMaybe();",
          "        default: throw new AssertionError(id);",
          "      }",
          "    }",
          "  }",
          "}");
    }
    Compilation compilation =
        compilerWithOptions(
            compilerMode)
            .compile(module, maybe, definitelyNot, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }
}
