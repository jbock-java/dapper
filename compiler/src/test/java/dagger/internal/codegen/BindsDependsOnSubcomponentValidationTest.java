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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/**
 * Tests to make sure that delegate bindings where the impl depends on a binding in a subcomponent
 * properly fail. These are regression tests for b/147020838.
 */
public class BindsDependsOnSubcomponentValidationTest {
  @Test
  public void testBinds() {
    JavaFileObject parentComponent =
        JavaFileObjects.forSourceLines(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface ParentComponent {",
            "  ChildComponent getChild();",
            "}");
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface ParentModule {",
            "  @Binds Foo bindFoo(FooImpl impl);",
            "}");
    JavaFileObject childComponent =
        JavaFileObjects.forSourceLines(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface ChildComponent {",
            "  Foo getFoo();",
            "}");
    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface ChildModule {",
            "  @Provides static Long providLong() {",
            "    return 0L;",
            "  }",
            "}");
    JavaFileObject iface =
        JavaFileObjects.forSourceLines("test.Foo", "package test;", "", "interface Foo {", "}");
    JavaFileObject impl =
        JavaFileObjects.forSourceLines(
            "test.FooImpl",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class FooImpl implements Foo {",
            "  @Inject FooImpl(Long l) {}",
            "}");
    Compilation compilation =
        daggerCompiler()
            .compile(parentComponent, parentModule, childComponent, childModule, iface, impl);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("Long cannot be provided without an @Inject constructor")
        .inFile(parentComponent)
        .onLineContaining("interface ParentComponent");
  }
}
