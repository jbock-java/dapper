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
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;

/** Producer-specific validation tests. */
public class ProductionGraphValidationTest {

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
