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

import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static io.jbock.testing.compile.CompilationSubject.assertThat;

import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AssistedErrorsTest {

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testAssistedNotWithAssistedInjectionConstructor(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "",
            "final class Foo {",
            "  Foo(",
            "      @Assisted String str",
            "  ) {}",
            "",
            "  void someMethod(",
            "      @Assisted int i",
            "  ) {}",
            "}");
    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(2);
    assertThat(compilation)
        .hadErrorContaining(
            "@Assisted parameters can only be used within an @AssistedInject-annotated constructor")
        .inFile(foo)
        .onLine(7);
    assertThat(compilation)
        .hadErrorContaining(
            "@Assisted parameters can only be used within an @AssistedInject-annotated constructor")
        .inFile(foo)
        .onLine(11);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testNestedFactoryNotStatic(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import jakarta.inject.Qualifier;",
            "",
            "class Foo {",
            "  @Qualifier @interface FooQualifier {}",
            "",
            "  @AssistedInject",
            "  Foo(",
            "      @FooQualifier @Assisted int i",
            "  ) {}",
            "}");
    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("Qualifiers cannot be used with @Assisted parameters.")
        .inFile(foo)
        .onLine(12);
  }
}
