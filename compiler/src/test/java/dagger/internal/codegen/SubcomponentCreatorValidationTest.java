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

import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.TestUtils.message;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.SUBCOMPONENT_BUILDER;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.SUBCOMPONENT_FACTORY;
import static dagger.internal.codegen.base.ComponentCreatorKind.BUILDER;
import static dagger.internal.codegen.base.ComponentCreatorKind.FACTORY;
import static dagger.internal.codegen.base.ComponentKind.SUBCOMPONENT;
import static dagger.internal.codegen.binding.ErrorMessages.ComponentCreatorMessages.moreThanOneRefToSubcomponent;
import static dagger.internal.codegen.binding.ErrorMessages.componentMessagesFor;
import static io.jbock.testing.compile.CompilationSubject.assertThat;

import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Tests for {@link dagger.Subcomponent.Builder} validation. */
class SubcomponentCreatorValidationTest {

  private static List<ComponentCreatorTestData> dataSource() {
    List<ComponentCreatorTestData> result = new ArrayList<>();
    result.add(new ComponentCreatorTestData(DEFAULT_MODE, SUBCOMPONENT_FACTORY));
    result.add(new ComponentCreatorTestData(DEFAULT_MODE, SUBCOMPONENT_BUILDER));
    return result;
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testRefSubcomponentAndSubCreatorFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  ChildComponent child();",
            "  ChildComponent.Builder childComponentBuilder();",
            "}");
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {",
        "  @Subcomponent.Builder",
        "  static interface Builder {",
        "    ChildComponent build();",
        "  }",
        "}");
    Compilation compilation = data.compile(componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                moreThanOneRefToSubcomponent(),
                "test.ChildComponent",
                data.process("[child(), childComponentBuilder()]")))
        .inFile(componentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testRefSubCreatorTwiceFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile = data.preprocessedJavaFile("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import jakarta.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder builder1();",
        "  ChildComponent.Builder builder2();",
        "}");
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {",
        "  @Subcomponent.Builder",
        "  static interface Builder {",
        "    ChildComponent build();",
        "  }",
        "}");
    Compilation compilation = data.compile(componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                moreThanOneRefToSubcomponent(),
                "test.ChildComponent", data.process("[builder1(), builder2()]")))
        .inFile(componentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testMoreThanOneCreatorFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile = data.preprocessedJavaFile("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import jakarta.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder1 build();",
        "}");
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {",
        "  @Subcomponent.Builder",
        "  static interface Builder1 {",
        "    ChildComponent build();",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  static interface Builder2 {",
        "    ChildComponent build();",
        "  }",
        "}");
    Compilation compilation = data.compile(componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                componentMessagesFor(SUBCOMPONENT).moreThanOne(),
                data.process("[test.ChildComponent.Builder1, test.ChildComponent.Builder2]")))
        .inFile(childComponentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testMoreThanOneCreatorFails_differentTypes(ComponentCreatorTestData data) {
    JavaFileObject componentFile = data.preprocessedJavaFile("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import jakarta.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder build();",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {",
        "  @Subcomponent.Builder",
        "  static interface Builder {",
        "    ChildComponent build();",
        "  }",
        "",
        "  @Subcomponent.Factory",
        "  static interface Factory {",
        "    ChildComponent create();",
        "  }",
        "}");
    Compilation compilation = data.compile(componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                componentMessagesFor(SUBCOMPONENT).moreThanOne(),
                "[test.ChildComponent.Builder, test.ChildComponent.Factory]"))
        .inFile(childComponentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorGenericsFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile = data.preprocessedJavaFile("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import jakarta.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder build();",
        "}");
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder<T> {",
        "     ChildComponent build();",
        "  }",
        "}");
    Compilation compilation = data.compile(componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(data.messages.generics()).inFile(childComponentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorNotInComponentFails(ComponentCreatorTestData data) {
    JavaFileObject builder = data.preprocessedJavaFile("test.Builder",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent.Builder",
        "interface Builder {}");
    Compilation compilation = data.compile(builder);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(data.messages.mustBeInComponent()).inFile(builder);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorMissingFactoryMethodFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  ChildComponent.Builder childComponentBuilder();",
            "}");
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {}",
        "}");
    Compilation compilation = data.compile(componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(data.messages.missingFactoryMethod())
        .inFile(childComponentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testPrivateCreatorFails(ComponentCreatorTestData data) {
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  private interface Builder {}",
        "}");
    Compilation compilation = data.compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(data.messages.isPrivate()).inFile(childComponentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testNonStaticCreatorFails(ComponentCreatorTestData data) {
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  abstract class Builder {}",
        "}");
    Compilation compilation = data.compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(data.messages.mustBeStatic()).inFile(childComponentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testNonAbstractCreatorFails(ComponentCreatorTestData data) {
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  static class Builder {}",
        "}");
    Compilation compilation = data.compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(data.messages.mustBeAbstract())
        .inFile(childComponentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorOneConstructorWithArgsFails(ComponentCreatorTestData data) {
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  static abstract class Builder {",
        "    Builder(String unused) {}",
        "  }",
        "}");
    Compilation compilation = data.compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(data.messages.invalidConstructor())
        .inFile(childComponentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorMoreThanOneConstructorFails(ComponentCreatorTestData data) {
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  static abstract class Builder {",
        "    Builder() {}",
        "    Builder(String unused) {}",
        "  }",
        "}");
    Compilation compilation = data.compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(data.messages.invalidConstructor())
        .inFile(childComponentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorEnumFails(ComponentCreatorTestData data) {
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  enum Builder {}",
        "}");
    Compilation compilation = data.compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(data.messages.mustBeClassOrInterface())
        .inFile(childComponentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorFactoryMethodReturnsWrongTypeFails(ComponentCreatorTestData data) {
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    String build();",
        "  }",
        "}");
    Compilation compilation = data.compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(data.messages.factoryMethodMustReturnComponentType())
        .inFile(childComponentFile)
        .onLine(9);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testInheritedCreatorFactoryMethodReturnsWrongTypeFails(ComponentCreatorTestData data) {
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    String build();",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");
    Compilation compilation = data.compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                data.messages.inheritedFactoryMethodMustReturnComponentType(), data.process("build")))
        .inFile(childComponentFile)
        .onLine(12);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testTwoFactoryMethodsFails(ComponentCreatorTestData data) {
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    ChildComponent build1();",
        "  }",
        "}");
    Compilation compilation = data.compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(String.format(data.messages.twoFactoryMethods(), data.process("build()")))
        .inFile(childComponentFile)
        .onLine(10);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testInheritedTwoFactoryMethodsFails(ComponentCreatorTestData data) {
    JavaFileObject childComponentFile = data.preprocessedJavaFile("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    ChildComponent build();",
        "    ChildComponent build1();",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");
    Compilation compilation = data.compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                data.messages.inheritedTwoFactoryMethods(), data.process("build()"), data.process("build1()")))
        .inFile(childComponentFile)
        .onLine(13);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testMultipleSettersPerTypeFails(ComponentCreatorTestData data) {
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
            "  @Provides String s() { return \"\"; }",
            "}");
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  ChildComponent.Builder childComponentBuilder();",
            "}");
    JavaFileObject childComponentFile =
        data.javaFileBuilder("test.ChildComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Subcomponent;",
                "import jakarta.inject.Provider;",
                "",
                "@Subcomponent(modules = TestModule.class)",
                "abstract class ChildComponent {",
                "  abstract String s();",
                "")
            .addLinesIf(
                BUILDER,
                "  @Subcomponent.Builder",
                "  interface Builder {",
                "    ChildComponent build();",
                "    void set1(TestModule s);",
                "    void set2(TestModule s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Subcomponent.Factory",
                "  interface Factory {",
                "    ChildComponent create(TestModule m1, TestModule m2);",
                "  }")
            .addLines("}")
            .build();
    Compilation compilation = data.compile(moduleFile, componentFile, childComponentFile);
    assertThat(compilation).failed();
    String elements =
        data.creatorKind.equals(BUILDER)
            ? "[void test.ChildComponent.Builder.set1(test.TestModule), "
            + "void test.ChildComponent.Builder.set2(test.TestModule)]"
            : "[test.TestModule m1, test.TestModule m2]";
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                data.messages.multipleSettersForModuleOrDependencyType(), "test.TestModule", elements))
        .inFile(childComponentFile)
        .onLine(11);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testMultipleSettersPerTypeIncludingResolvedGenericsFails(ComponentCreatorTestData data) {
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
            "  @Provides String s() { return \"\"; }",
            "}");
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  ChildComponent.Builder childComponentBuilder();",
            "}");
    JavaFileObject childComponentFile =
        data.javaFileBuilder("test.ChildComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Subcomponent;",
                "import jakarta.inject.Provider;",
                "",
                "@Subcomponent(modules = TestModule.class)",
                "abstract class ChildComponent {",
                "  abstract String s();",
                "")
            .addLinesIf(
                BUILDER,
                "  interface Parent<T> {",
                "    void set1(T t);",
                "  }",
                "",
                "  @Subcomponent.Builder",
                "  interface Builder extends Parent<TestModule> {",
                "    ChildComponent build();",
                "    void set2(TestModule s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  interface Parent<C, T> {",
                "    C create(TestModule m1, T t);",
                "  }",
                "",
                "  @Subcomponent.Factory",
                "  interface Factory extends Parent<ChildComponent, TestModule> {}")
            .addLines("}")
            .build();
    Compilation compilation = data.compile(moduleFile, componentFile, childComponentFile);
    assertThat(compilation).failed();
    String elements =
        data.creatorKind.equals(BUILDER)
            ? "[void test.ChildComponent.Builder.set1(test.TestModule), "
            + "void test.ChildComponent.Builder.set2(test.TestModule)]"
            : "[test.TestModule m1, test.TestModule t]";
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                data.messages.multipleSettersForModuleOrDependencyType(), "test.TestModule", elements))
        .inFile(childComponentFile)
        .onLine(15);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testMultipleSettersPerBoundInstanceTypeFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  ChildComponent.Builder childComponentBuilder();",
            "}");
    JavaFileObject childComponentFile =
        data.javaFileBuilder("test.ChildComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.BindsInstance;",
                "import dagger.Subcomponent;",
                "",
                "@Subcomponent",
                "abstract class ChildComponent {",
                "  abstract String s();",
                "")
            .addLinesIf(
                BUILDER,
                "  @Subcomponent.Builder",
                "  interface Builder {",
                "    ChildComponent build();",
                "    @BindsInstance void set1(String s);",
                "    @BindsInstance void set2(String s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Subcomponent.Factory",
                "  interface Factory {",
                "    ChildComponent create(",
                "        @BindsInstance String s1, @BindsInstance String s2);",
                "  }")
            .addLines("}")
            .build();

    Compilation compilation = data.compile(componentFile, childComponentFile);
    assertThat(compilation).failed();
    String firstBinding = data.creatorKind.equals(FACTORY)
        ? "ChildComponent.Factory.create(s1, \u2026)"
        : "@BindsInstance void ChildComponent.Builder.set1(String)";
    String secondBinding = data.creatorKind.equals(FACTORY)
        ? "ChildComponent.Factory.create(\u2026, s2)"
        : "@BindsInstance void ChildComponent.Builder.set2(String)";
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "String is bound multiple times:",
                "    " + firstBinding,
                "    " + secondBinding,
                "    in component: [ParentComponent \u2192 ChildComponent]"))
        .inFile(componentFile)
        .onLineContaining("interface ParentComponent {");
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testExtraSettersFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile = data.preprocessedJavaFile("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import jakarta.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder build();",
        "}");
    JavaFileObject childComponentFile =
        data.javaFileBuilder("test.ChildComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Subcomponent;",
                "import jakarta.inject.Provider;",
                "",
                "@Subcomponent",
                "abstract class ChildComponent {")
            .addLinesIf(
                BUILDER,
                "  @Subcomponent.Builder",
                "  interface Builder {",
                "    ChildComponent build();",
                "    void set1(String s);",
                "    void set2(Integer s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Subcomponent.Factory",
                "  interface Factory {",
                "    ChildComponent create(String s, Integer i);",
                "  }")
            .addLines("}")
            .build();
    Compilation compilation = data.compile(componentFile, childComponentFile);
    assertThat(compilation).failed();
    String elements =
        data.creatorKind.equals(FACTORY)
            ? "[String s, Integer i]"
            : "[void test.ChildComponent.Builder.set1(String),"
            + " void test.ChildComponent.Builder.set2(Integer)]";
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                data.messages.extraSetters(),
                elements))
        .inFile(childComponentFile)
        .onLine(9);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testMissingSettersFail(ComponentCreatorTestData data) {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  TestModule(String unused) {}",
        "  @Provides String s() { return null; }",
        "}");
    JavaFileObject module2File = JavaFileObjects.forSourceLines("test.Test2Module",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class Test2Module {",
        "  @Provides Integer i() { return null; }",
        "}");
    JavaFileObject module3File = JavaFileObjects.forSourceLines("test.Test3Module",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class Test3Module {",
        "  Test3Module(String unused) {}",
        "  @Provides Double d() { return null; }",
        "}");
    JavaFileObject componentFile = data.preprocessedJavaFile("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import jakarta.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder build();",
        "}");
    JavaFileObject childComponentFile =
        data.preprocessedJavaFile(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = {TestModule.class, Test2Module.class, Test3Module.class})",
            "interface ChildComponent {",
            "  String string();",
            "  Integer integer();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    ChildComponent build();",
            "  }",
            "}");
    Compilation compilation =
        data.compile(moduleFile, module2File, module3File, componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            // Ignores Test2Module because we can construct it ourselves.
            // TODO(sameb): Ignore Test3Module because it's not used within transitive dependencies.
            String.format(data.messages.missingSetters(), "[test.TestModule, test.Test3Module]"))
        .inFile(childComponentFile)
        .onLine(11);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void covariantFactoryMethodReturnType(ComponentCreatorTestData data) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "test.Supertype",
            "package test;",
            "",
            "interface Supertype {",
            "  Foo foo();",
            "}");

    JavaFileObject subcomponent =
        data.preprocessedJavaFile(
            "test.HasSupertype",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface HasSupertype extends Supertype {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Supertype build();",
            "  }",
            "}");

    Compilation compilation = data.compile(foo, supertype, subcomponent);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void covariantFactoryMethodReturnType_hasNewMethod(ComponentCreatorTestData data) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "test.Supertype",
            "package test;",
            "",
            "interface Supertype {",
            "  Foo foo();",
            "}");

    JavaFileObject subcomponent =
        data.preprocessedJavaFile(
            "test.HasSupertype",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface HasSupertype extends Supertype {",
            "  Bar bar();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Supertype build();",
            "  }",
            "}");

    Compilation compilation = data.compile(foo, bar, supertype, subcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining(
            data.process(
                "test.HasSupertype.Builder.build() returns test.Supertype, but test.HasSupertype "
                    + "declares additional component method(s): bar(). In order to provide "
                    + "type-safe access to these methods, override build() to return "
                    + "test.HasSupertype"))
        .inFile(subcomponent)
        .onLine(11);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void covariantFactoryMethodReturnType_hasNewMethod_buildMethodInherited(ComponentCreatorTestData data) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "test.Supertype",
            "package test;",
            "",
            "interface Supertype {",
            "  Foo foo();",
            "}");

    JavaFileObject creatorSupertype =
        data.preprocessedJavaFile(
            "test.CreatorSupertype",
            "package test;",
            "",
            "interface CreatorSupertype {",
            "  Supertype build();",
            "}");

    JavaFileObject subcomponent =
        data.preprocessedJavaFile(
            "test.HasSupertype",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface HasSupertype extends Supertype {",
            "  Bar bar();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder extends CreatorSupertype {}",
            "}");

    Compilation compilation = data.compile(foo, bar, supertype, creatorSupertype, subcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining(
            data.process(
                "[test.CreatorSupertype.build()] test.HasSupertype.Builder.build() returns "
                    + "test.Supertype, but test.HasSupertype declares additional component "
                    + "method(s): bar(). In order to provide type-safe access to these methods, "
                    + "override build() to return test.HasSupertype"));
  }
}
