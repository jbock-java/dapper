/*
 * Copyright (C) 2015 The Dagger Authors.
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
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.COMPONENT_FACTORY;
import static dagger.internal.codegen.binding.ErrorMessages.creatorMessagesFor;
import static io.jbock.testing.compile.CompilationSubject.assertThat;

import dagger.internal.codegen.binding.ErrorMessages;
import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for {@link dagger.Component.Factory} */
class ComponentFactoryTest {

  private static final ErrorMessages.ComponentCreatorMessages MSGS =
      creatorMessagesFor(COMPONENT_FACTORY);

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testUsesParameterNames(CompilerMode compilerMode) {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides String string() { return null; }",
            "}");

    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  String string();",
            "",
            "  @Component.Factory",
            "  interface Factory {",
            "    TestComponent newTestComponent(TestModule mod);",
            "  }",
            "}");
    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;",
        "",
        "import dagger.internal.Preconditions;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {",
        "  private static final class Factory implements TestComponent.Factory {",
        "    @Override",
        "    public TestComponent newTestComponent(TestModule mod) {",
        "      Preconditions.checkNotNull(mod);",
        "      return new DaggerTestComponent(mod);",
        "    }",
        "  }",
        "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(moduleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testSetterMethodFails(CompilerMode compilerMode) {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  @Component.Factory",
            "  interface Factory {",
            "    SimpleComponent create();",
            "    Factory set(String s);",
            "  }",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(String.format(MSGS.twoFactoryMethods(), "create()"))
        .inFile(componentFile)
        .onLineContaining("Factory set(String s);");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testInheritedSetterMethodFails(CompilerMode compilerMode) {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  interface Parent {",
            "    SimpleComponent create();",
            "    Parent set(String s);",
            "  }",
            "",
            "  @Component.Factory",
            "  interface Factory extends Parent {}",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(String.format(MSGS.twoFactoryMethods(), "create()"))
        .inFile(componentFile)
        .onLineContaining("interface Factory");
  }
}
