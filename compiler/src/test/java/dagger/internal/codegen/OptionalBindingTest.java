/*
 * Copyright (C) 2016 The Dagger Authors.
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

class OptionalBindingTest {
  @Test
  void provideExplicitOptionalInParent_AndBindsOptionalOfInChild() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Optional;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Optional<String> optional();",
            "  Child child();",
            "}");
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Optional;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides",
            "  Optional<String> optional() {",
            "    return Optional.of(new String());",
            "  }",
            "}");

    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Optional;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Optional<String> optional();",
            "}");
    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Optional;",
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides",
            "  Optional<String> optionalString() { return null; }",
            "}");

    Compilation compilation = daggerCompiler().compile(parent, parentModule, child, childModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Optional<String> is bound multiple times")
        .inFile(parent)
        .onLineContaining("interface Parent");
  }
}
