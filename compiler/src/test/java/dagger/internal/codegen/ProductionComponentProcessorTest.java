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

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.tools.JavaFileObject;
import java.util.Collection;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.Compilers.daggerCompiler;

@RunWith(Parameterized.class)
public class ProductionComponentProcessorTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ProductionComponentProcessorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void componentOnConcreteClass() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent",
        "final class NotAComponent {}");
    Compilation compilation = daggerCompiler().compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
  }

  @Test
  public void componentOnEnum() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent",
        "enum NotAComponent {",
        "  INSTANCE",
        "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
  }

  @Test
  public void componentOnAnnotation() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent",
        "@interface NotAComponent {}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
  }

  @Test
  public void nonModuleModule() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent(modules = Object.class)",
        "interface NotAComponent {}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("is not annotated with one of @Module, @ProducerModule");
  }
}
