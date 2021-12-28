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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.Compilers.daggerCompiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/** Producer-specific validation tests. */
public class ProductionGraphValidationTest {
  private static final JavaFileObject EXECUTOR_MODULE =
      JavaFileObjects.forSourceLines(
          "test.ExecutorModule",
          "package test;",
          "",
          "import com.google.common.util.concurrent.MoreExecutors;",
          "import dagger.Module;",
          "import dagger.Provides;",
          "import dagger.producers.Production;",
          "import java.util.concurrent.Executor;",
          "",
          "@Module",
          "class ExecutorModule {",
          "  @Provides @Production Executor executor() {",
          "    return MoreExecutors.directExecutor();",
          "  }",
          "}");

  @Test
  public void componentProductionWithNoDependencyChain() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProductionComponent;",
        "",
        "final class TestClass {",
        "  interface A {}",
        "",
        "  @ProductionComponent(modules = ExecutorModule.class)",
        "  interface AComponent {",
        "    ListenableFuture<A> getA();",
        "  }",
        "}");

    Compilation compilation = daggerCompiler().compile(EXECUTOR_MODULE, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "TestClass.A cannot be provided without an @Provides- or @Produces-annotated "
                + "method.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test
  public void componentWithBadModule() {
    JavaFileObject badModule =
        JavaFileObjects.forSourceLines(
            "test.BadModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.multibindings.Multibinds;",
            "import dagger.Module;",
            "import java.util.Set;",
            "",
            "@Module",
            "abstract class BadModule {",
            "  @Multibinds",
            "  @BindsOptionalOf",
            "  abstract Set<String> strings();",
            "}");
    JavaFileObject badComponent =
        JavaFileObjects.forSourceLines(
            "test.BadComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Optional;",
            "import java.util.Set;",
            "",
            "@Component(modules = BadModule.class)",
            "interface BadComponent {",
            "  Set<String> strings();",
            "  Optional<Set<String>> optionalStrings();",
            "}");
    Compilation compilation = daggerCompiler().compile(badModule, badComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("BadModule has errors")
        .inFile(badComponent)
        .onLine(7);
  }
}
