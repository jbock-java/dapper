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

import static dagger.internal.codegen.Compilers.daggerCompiler;
import static io.jbock.testing.compile.CompilationSubject.assertThat;

import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

public class MultipleRequestTest {
  private static final JavaFileObject DEP_FILE = JavaFileObjects.forSourceLines("test.Dep",
      "package test;",
      "",
      "import jakarta.inject.Inject;",
      "",
      "class Dep {",
      "  @Inject Dep() {}",
      "}");

  @Test
  public void multipleRequests_constructor() {
    Compilation compilation =
        daggerCompiler()
            .compile(
                DEP_FILE,
                JavaFileObjects.forSourceLines(
                    "test.ConstructorInjectsMultiple",
                    "package test;",
                    "",
                    "import jakarta.inject.Inject;",
                    "",
                    "class ConstructorInjectsMultiple {",
                    "  @Inject ConstructorInjectsMultiple(Dep d1, Dep d2) {}",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.SimpleComponent",
                    "package test;",
                    "",
                    "import dagger.Component;",
                    "",
                    "@Component",
                    "interface SimpleComponent {",
                    "  ConstructorInjectsMultiple get();",
                    "}"));
    assertThat(compilation).succeeded();
  }

  @Test
  public void multipleRequests_providesMethod() {
    Compilation compilation =
        daggerCompiler()
            .compile(
                DEP_FILE,
                JavaFileObjects.forSourceLines(
                    "test.FieldInjectsMultiple",
                    "package test;",
                    "",
                    "import dagger.Module;",
                    "import dagger.Provides;",
                    "",
                    "@Module",
                    "class SimpleModule {",
                    "  @Provides Object provide(Dep d1, Dep d2) {",
                    "    return null;",
                    "  }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.SimpleComponent",
                    "package test;",
                    "",
                    "import dagger.Component;",
                    "",
                    "@Component(modules = SimpleModule.class)",
                    "interface SimpleComponent {",
                    "  Object get();",
                    "}"));
    assertThat(compilation).succeeded();
  }
}
