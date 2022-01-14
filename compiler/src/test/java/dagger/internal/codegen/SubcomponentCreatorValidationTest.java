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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.TestUtils.message;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.SUBCOMPONENT_BUILDER;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.SUBCOMPONENT_FACTORY;
import static dagger.internal.codegen.binding.ComponentCreatorKind.BUILDER;
import static dagger.internal.codegen.binding.ComponentCreatorKind.FACTORY;
import static dagger.internal.codegen.binding.ComponentKind.SUBCOMPONENT;
import static dagger.internal.codegen.binding.ErrorMessages.ComponentCreatorMessages.moreThanOneRefToSubcomponent;
import static dagger.internal.codegen.binding.ErrorMessages.componentMessagesFor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.internal.codegen.binding.ComponentCreatorAnnotation;
import java.util.Arrays;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for {@link dagger.Subcomponent.Builder} validation. */
@RunWith(Parameterized.class)
public class SubcomponentCreatorValidationTest {

  private final ComponentCreatorTestData data;

  @Parameters(name = "creatorKind={0}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(new Object[][]{{SUBCOMPONENT_BUILDER}, {SUBCOMPONENT_FACTORY}});
  }

  public SubcomponentCreatorValidationTest(ComponentCreatorAnnotation componentCreatorAnnotation) {
    this.data = new ComponentCreatorTestData(DEFAULT_MODE, componentCreatorAnnotation);
  }

  @Test
  public void testRefSubcomponentAndSubCreatorFails() {
    JavaFileObject componentFile = data.preprocessedJavaFile("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import jakarta.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent child();",
        "  ChildComponent.Builder builder();",
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
                data.process("[child(), builder()]")))
        .inFile(componentFile);
  }

  @Test
  public void testRefSubCreatorTwiceFails() {
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

  @Test
  public void testMoreThanOneCreatorFails() {
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

  @Test
  public void testMoreThanOneCreatorFails_differentTypes() {
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

  @Test
  public void testCreatorGenericsFails() {
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

  @Test
  public void testCreatorNotInComponentFails() {
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

  @Test
  public void testCreatorMissingFactoryMethodFails() {
    JavaFileObject componentFile = data.preprocessedJavaFile("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import jakarta.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder builder();",
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

  @Test
  public void testPrivateCreatorFails() {
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

  @Test
  public void testNonStaticCreatorFails() {
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

  @Test
  public void testNonAbstractCreatorFails() {
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

  @Test
  public void testCreatorOneConstructorWithArgsFails() {
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

  @Test
  public void testCreatorMoreThanOneConstructorFails() {
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

  @Test
  public void testCreatorEnumFails() {
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

  @Test
  public void testCreatorFactoryMethodReturnsWrongTypeFails() {
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

  @Test
  public void testInheritedCreatorFactoryMethodReturnsWrongTypeFails() {
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

  @Test
  public void testTwoFactoryMethodsFails() {
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

  @Test
  public void testInheritedTwoFactoryMethodsFails() {
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

  @Test
  public void testMultipleSettersPerTypeFails() {
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
            .addLines( //
                "}")
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

  @Test
  public void testMultipleSettersPerTypeIncludingResolvedGenericsFails() {
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
            .addLines( //
                "}")
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

  @Test
  public void testMultipleSettersPerBoundInstanceTypeFails() {
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
            .addLines( //
                "}")
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

  @Test
  public void testExtraSettersFails() {
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
            .addLines( //
                "}")
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

  @Test
  public void testMissingSettersFail() {
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

  @Test
  public void covariantFactoryMethodReturnType() {
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

  @Test
  public void covariantFactoryMethodReturnType_hasNewMethod() {
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

  @Test
  public void covariantFactoryMethodReturnType_hasNewMethod_buildMethodInherited() {
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
