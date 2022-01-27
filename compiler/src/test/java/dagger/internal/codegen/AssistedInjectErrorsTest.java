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

class AssistedInjectErrorsTest {

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testAssistedInjectWithDuplicateTypesFails(CompilerMode compilerMode) {
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
            "  Foo(@Assisted String str1, @Assisted String str2) {}",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@AssistedInject constructor has duplicate @Assisted type: @Assisted java.lang.String")
        .inFile(foo)
        .onLine(8);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testAssistedInjectWithDuplicateTypesEmptyQualifierFails(CompilerMode compilerMode) {
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
            "  Foo(@Assisted(\"\") String str1, @Assisted String str2) {}",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@AssistedInject constructor has duplicate @Assisted type: @Assisted java.lang.String")
        .inFile(foo)
        .onLine(8);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testAssistedInjectWithDuplicateQualifiedTypesFails(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo<T> {",
            "  @AssistedInject",
            "  Foo(@Assisted(\"MyQualfier\") String s1, @Assisted(\"MyQualfier\") String s2) {}",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@AssistedInject constructor has duplicate @Assisted type: "
                + "@Assisted(\"MyQualfier\") java.lang.String")
        .inFile(foo)
        .onLine(8);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testAssistedInjectWithDuplicateGenericTypesFails(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import java.util.List;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted List<String> list1, @Assisted List<String> list2) {}",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@AssistedInject constructor has duplicate @Assisted type: "
                + "@Assisted java.util.List<java.lang.String>")
        .inFile(foo)
        .onLine(9);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testAssistedInjectWithDuplicateParameterizedTypesFails(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo<T> {",
            "  @AssistedInject",
            "  Foo(@Assisted T t1, @Assisted T t2) {}",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("@AssistedInject constructor has duplicate @Assisted type: @Assisted T")
        .inFile(foo)
        .onLine(8);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testAssistedInjectWithUniqueParameterizedTypesPasses(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import java.util.List;",
            "",
            "class Foo<T1, T2> {",
            "  @AssistedInject",
            "  Foo(@Assisted T1 t1, @Assisted T2 t2) {}",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).succeeded();
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testAssistedInjectWithUniqueGenericTypesPasses(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import java.util.List;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted List<String> list1, @Assisted List<Integer> list2) {}",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).succeeded();
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testAssistedInjectWithUniqueQualifiedTypesPasses(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import java.util.List;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(",
            "      @Assisted(\"1\") Integer i1,",
            "      @Assisted(\"1\") String s1,",
            "      @Assisted(\"2\") String s2,",
            "      @Assisted String s3) {}",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).succeeded();
  }
}
