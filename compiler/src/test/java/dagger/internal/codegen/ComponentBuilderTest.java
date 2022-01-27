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
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.COMPONENT_BUILDER;
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

/** Tests for {@link dagger.Component.Builder} */
class ComponentBuilderTest {

  private static final ErrorMessages.ComponentCreatorMessages MSGS =
      creatorMessagesFor(COMPONENT_BUILDER);

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testUsesBuildAndSetterNames(CompilerMode compilerMode) {
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
            "  @Component.Builder",
            "  interface Builder {",
            "    Builder setTestModule(TestModule testModule);",
            "    TestComponent create();",
            "  }",
            "}");
    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        "import dagger.internal.Preconditions;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {",
        "  private static final class Builder implements TestComponent.Builder {",
        "    private TestModule testModule;",
        "",
        "    @Override",
        "    public Builder setTestModule(TestModule testModule) {",
        "      this.testModule = Preconditions.checkNotNull(testModule);",
        "      return this;",
        "    }",
        "",
        "    @Override",
        "    public TestComponent create() {",
        "      if (testModule == null) {",
        "        this.testModule = new TestModule();",
        "      }",
        "      return new DaggerTestComponent(testModule);",
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
  void testSetterMethodWithMoreThanOneArgFails(CompilerMode compilerMode) {
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
            "  @Component.Builder",
            "  interface Builder {",
            "    SimpleComponent build();",
            "    Builder set(String s, Integer i);",
            "    Builder set(Number n, Double d);",
            "  }",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.setterMethodsMustTakeOneArg())
        .inFile(componentFile)
        .onLineContaining("Builder set(String s, Integer i);");
    assertThat(compilation)
        .hadErrorContaining(MSGS.setterMethodsMustTakeOneArg())
        .inFile(componentFile)
        .onLineContaining("Builder set(Number n, Double d);");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testInheritedSetterMethodWithMoreThanOneArgFails(CompilerMode compilerMode) {
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
            "    SimpleComponent build();",
            "    Builder set1(String s, Integer i);",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                MSGS.inheritedSetterMethodsMustTakeOneArg(),
                "set1(java.lang.String,java.lang.Integer)"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testSetterReturningNonVoidOrBuilderFails(CompilerMode compilerMode) {
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
            "  @Component.Builder",
            "  interface Builder {",
            "    SimpleComponent build();",
            "    String set(Integer i);",
            "  }",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.setterMethodsMustReturnVoidOrBuilder())
        .inFile(componentFile)
        .onLineContaining("String set(Integer i);");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testInheritedSetterReturningNonVoidOrBuilderFails(CompilerMode compilerMode) {
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
            "    SimpleComponent build();",
            "    String set(Integer i);",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                MSGS.inheritedSetterMethodsMustReturnVoidOrBuilder(), "set(java.lang.Integer)"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testGenericsOnSetterMethodFails(CompilerMode compilerMode) {
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
            "  @Component.Builder",
            "  interface Builder {",
            "    SimpleComponent build();",
            "    <T> Builder set(T t);",
            "  }",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.methodsMayNotHaveTypeParameters())
        .inFile(componentFile)
        .onLineContaining("<T> Builder set(T t);");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testGenericsOnInheritedSetterMethodFails(CompilerMode compilerMode) {
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
            "    SimpleComponent build();",
            "    <T> Builder set(T t);",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(MSGS.inheritedMethodsMayNotHaveTypeParameters(), "<T>set(T)"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testBindsInstanceNotAllowedOnBothSetterAndParameter(CompilerMode compilerMode) {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  abstract String s();",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    @BindsInstance",
            "    Builder s(@BindsInstance String s);",
            "",
            "    SimpleComponent build();",
            "  }",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.bindsInstanceNotAllowedOnBothSetterMethodAndParameter())
        .inFile(componentFile)
        .onLineContaining("Builder s(");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testBindsInstanceNotAllowedOnBothSetterAndParameter_inherited(CompilerMode compilerMode) {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  abstract String s();",
            "",
            "  interface BuilderParent<B extends BuilderParent> {",
            "    @BindsInstance",
            "    B s(@BindsInstance String s);",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends BuilderParent<Builder> {",
            "    SimpleComponent build();",
            "  }",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                MSGS.inheritedBindsInstanceNotAllowedOnBothSetterMethodAndParameter(),
                "s(java.lang.String)"))
        .inFile(componentFile)
        .onLineContaining("Builder extends BuilderParent<Builder>");
  }
}
