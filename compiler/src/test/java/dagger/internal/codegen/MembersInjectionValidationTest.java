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

package dagger.internal.codegen;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/**
 * Tests that errors are reported for invalid members injection methods and {@link
 * dagger.MembersInjector} dependency requests.
 */
class MembersInjectionValidationTest {

  @Test
  void membersInjectorOfArray() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "",
            "@Component",
            "interface TestComponent {",
            "  MembersInjector<Object[]> objectArrayInjector();",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Cannot inject members into java.lang.Object[]")
        .inFile(component)
        .onLineContaining("objectArrayInjector();");
  }

  @Test
  void qualifiedMembersInjector() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "import jakarta.inject.Named;",
            "",
            "@Component",
            "interface TestComponent {",
            "  @Named(\"foo\") MembersInjector<Object> objectInjector();",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Cannot inject members into qualified types")
        .inFile(component)
        .onLineContaining("objectInjector();");
  }
}
