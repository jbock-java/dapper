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

import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.TestUtils.message;
import static io.jbock.testing.compile.CompilationSubject.assertThat;

import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class MissingBindingValidationTest {
  @Test
  void dependOnInterface() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.MyComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface MyComponent {",
        "  Foo getFoo();",
        "}");
    JavaFileObject injectable = JavaFileObjects.forSourceLines("test.Foo",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class Foo {",
        "  @Inject Foo(Bar bar) {}",
        "}");
    JavaFileObject nonInjectable = JavaFileObjects.forSourceLines("test.Bar",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "interface Bar {}");
    Compilation compilation = daggerCompiler().compile(component, injectable, nonInjectable);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("Bar cannot be provided without a @Provides-annotated method.")
        .inFile(component)
        .onLineContaining("interface MyComponent");
  }

  @Test
  void entryPointDependsOnInterface() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "final class TestClass {",
            "  interface A {}",
            "",
            "  @Component()",
            "  interface AComponent {",
            "    A getA();",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "\033[1;31m[Dagger/MissingBinding]\033[0m TestClass.A cannot be provided "
                + "without a @Provides-annotated method.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test
  void entryPointDependsOnQualifiedInterface() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Qualifier;",
            "",
            "final class TestClass {",
            "  @Qualifier @interface Q {}",
            "  interface A {}",
            "",
            "  @Component()",
            "  interface AComponent {",
            "    @Q A qualifiedA();",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "\033[1;31m[Dagger/MissingBinding]\033[0m @TestClass.Q TestClass.A cannot be provided "
                + "without a @Provides-annotated method.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test
  void constructorInjectionWithoutAnnotation() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import jakarta.inject.Inject;",
        "",
        "final class TestClass {",
        "  static class A {",
        "    A() {}",
        "  }",
        "",
        "  @Component()",
        "  interface AComponent {",
        "    A getA();",
        "  }",
        "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "TestClass.A cannot be provided without an @Inject constructor or a "
                + "@Provides-annotated method.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test
  void genericInjectClassWithWildcardDependencies() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Foo<? extends Number> foo();",
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class Foo<T> {",
            "  @Inject Foo(T t) {}",
            "}");
    Compilation compilation = daggerCompiler().compile(component, foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "Foo<? extends Number> cannot be provided "
                + "without a @Provides-annotated method");
  }

  @Test
  void bindsMethodAppearsInTrace() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "TestComponent",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  TestInterface testInterface();",
            "}");
    JavaFileObject interfaceFile =
        JavaFileObjects.forSourceLines("TestInterface", "interface TestInterface {}");
    JavaFileObject implementationFile =
        JavaFileObjects.forSourceLines(
            "TestImplementation",
            "import jakarta.inject.Inject;",
            "",
            "final class TestImplementation implements TestInterface {",
            "  @Inject TestImplementation(String missingBinding) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "TestModule",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Binds abstract TestInterface bindTestInterface(TestImplementation implementation);",
            "}");

    Compilation compilation =
        daggerCompiler().compile(component, module, interfaceFile, implementationFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "String cannot be provided without an @Inject constructor or a "
                    + "@Provides-annotated method.",
                "    String is injected at",
                "        TestImplementation(missingBinding)",
                "    TestImplementation is injected at",
                "        TestModule.bindTestInterface(implementation)",
                "    TestInterface is requested at",
                "        TestComponent.testInterface()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  void resolvedParametersInDependencyTrace() {
    JavaFileObject generic = JavaFileObjects.forSourceLines("test.Generic",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Provider;",
        "",
        "final class Generic<T> {",
        "  @Inject Generic(T t) {}",
        "}");
    JavaFileObject testClass = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "import java.util.List;",
        "",
        "final class TestClass {",
        "  @Inject TestClass(List list) {}",
        "}");
    JavaFileObject usesTest = JavaFileObjects.forSourceLines("test.UsesTest",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class UsesTest {",
        "  @Inject UsesTest(Generic<TestClass> genericTestClass) {}",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  UsesTest usesTest();",
        "}");

    Compilation compilation = daggerCompiler().compile(generic, testClass, usesTest, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "List cannot be provided without a @Provides-annotated method.",
                "    List is injected at",
                "        TestClass(list)",
                "    TestClass is injected at",
                "        Generic(t)",
                "    Generic<TestClass> is injected at",
                "        UsesTest(genericTestClass)",
                "    UsesTest is requested at",
                "        TestComponent.usesTest()"));
  }

  @Test
  void resolvedVariablesInDependencyTrace() {
    JavaFileObject generic = JavaFileObjects.forSourceLines("test.Generic",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Provider;",
        "",
        "final class Generic<T> {",
        "  @Inject Generic(T t) {}",
        "}");
    JavaFileObject testClass = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "import java.util.List;",
        "",
        "final class TestClass {",
        "  @Inject TestClass(List list) {}",
        "}");
    JavaFileObject usesTest = JavaFileObjects.forSourceLines("test.UsesTest",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class UsesTest {",
        "  @Inject UsesTest(Generic<TestClass> genericTestClass) {}",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  UsesTest usesTest();",
        "}");

    Compilation compilation = daggerCompiler().compile(generic, testClass, usesTest, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "List cannot be provided without a @Provides-annotated method.",
                "    List is injected at",
                "        TestClass(list)",
                "    TestClass is injected at",
                "        Generic(t)",
                "    Generic<TestClass> is injected at",
                "        UsesTest(genericTestClass)",
                "    UsesTest is requested at",
                "        TestComponent.usesTest()"));
  }

  @Test
  void bindingUsedOnlyInSubcomponentDependsOnBindingOnlyInSubcomponent() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "Parent",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
            "ParentModule",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides static Object needsString(String string) {",
            "    return \"needs string: \" + string;",
            "  }",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "Child",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  String string();",
            "  Object needsString();",
            "}");
    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
            "ChildModule",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides static String string() {",
            "    return \"child string\";",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(parent, parentModule, child, childModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContainingMatch(
            "(?s)\\QString cannot be provided\\E.*\\Q[Child] Child.needsString()\\E")
        .inFile(parent)
        .onLineContaining("interface Parent");
  }

  @Test
  void manyDependencies() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Object object();",
            "  String string();",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Binds abstract Object object(NotBound notBound);",
            "",
            "  @Provides static String string(NotBound notBound, Object object) {",
            "    return notBound.toString();",
            "  }",
            "}");
    JavaFileObject notBound =
        JavaFileObjects.forSourceLines(
            "test.NotBound", //
            "package test;",
            "",
            "interface NotBound {}");
    Compilation compilation = daggerCompiler().compile(component, module, notBound);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "\033[1;31m[Dagger/MissingBinding]\033[0m "
                    + "NotBound cannot be provided without a @Provides-annotated method.",
                "    NotBound is injected at",
                "        TestModule.object(notBound)",
                "    Object is requested at",
                "        TestComponent.object()",
                "It is also requested at:",
                "    TestModule.string(notBound, \u2026)",
                "The following other entry points also depend on it:",
                "    TestComponent.string()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  void tooManyRequests() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class Foo {",
            "  @Inject Foo(",
            "      String one,",
            "      String two,",
            "      String three,",
            "      String four,",
            "      String five,",
            "      String six,",
            "      String seven,",
            "      String eight,",
            "      String nine,",
            "      String ten,",
            "      String eleven,",
            "      String twelve,",
            "      String thirteen) {",
            "  }",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  String string();",
            "  Foo foo();",
            "}");

    Compilation compilation = daggerCompiler().compile(foo, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "\033[1;31m[Dagger/MissingBinding]\033[0m String cannot be provided without an "
                    + "@Inject constructor or a @Provides-annotated method.",
                "    String is requested at",
                "        TestComponent.string()",
                "It is also requested at:",
                "    Foo(one, \u2026)",
                "    Foo(\u2026, two, \u2026)",
                "    Foo(\u2026, three, \u2026)",
                "    Foo(\u2026, four, \u2026)",
                "    Foo(\u2026, five, \u2026)",
                "    Foo(\u2026, six, \u2026)",
                "    Foo(\u2026, seven, \u2026)",
                "    Foo(\u2026, eight, \u2026)",
                "    Foo(\u2026, nine, \u2026)",
                "    Foo(\u2026, ten, \u2026)",
                "    and 3 others"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  void tooManyEntryPoints() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  String string1();",
            "  String string2();",
            "  String string3();",
            "  String string4();",
            "  String string5();",
            "  String string6();",
            "  String string7();",
            "  String string8();",
            "  String string9();",
            "  String string10();",
            "  String string11();",
            "  String string12();",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "\033[1;31m[Dagger/MissingBinding]\033[0m String cannot be provided without an "
                    + "@Inject constructor or a @Provides-annotated method.",
                "    String is requested at",
                "        TestComponent.string1()",
                "The following other entry points also depend on it:",
                "    TestComponent.string2()",
                "    TestComponent.string3()",
                "    TestComponent.string4()",
                "    TestComponent.string5()",
                "    TestComponent.string6()",
                "    TestComponent.string7()",
                "    TestComponent.string8()",
                "    TestComponent.string9()",
                "    TestComponent.string10()",
                "    TestComponent.string11()",
                "    and 1 other"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  void missingBindingInAllComponentsAndEntryPoints() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "Parent",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Foo foo();",
            "  Bar bar();",
            "  Child child();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "Child",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child {",
            "  Foo foo();",
            "  Baz baz();",
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "Foo",
            "import jakarta.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo(Bar bar) {}",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "Bar",
            "import jakarta.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar(Baz baz) {}",
            "}");
    JavaFileObject baz = JavaFileObjects.forSourceLines("Baz", "class Baz {}");

    Compilation compilation = daggerCompiler().compile(parent, child, foo, bar, baz);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "\033[1;31m[Dagger/MissingBinding]\033[0m Baz cannot be provided without an "
                    + "@Inject constructor or a @Provides-annotated method.",
                "    Baz is injected at",
                "        Bar(baz)",
                "    Bar is requested at",
                "        Parent.bar()",
                "The following other entry points also depend on it:",
                "    Parent.foo()",
                "    Child.foo() [Parent \u2192 Child]",
                "    Child.baz() [Parent \u2192 Child]"))
        .inFile(parent)
        .onLineContaining("interface Parent");
  }

  // Regression test for b/147423208 where if the same subcomponent was used
  // in two different parts of the hierarchy and only one side had a missing binding
  // incorrect caching during binding graph conversion might cause validation to pass
  // incorrectly.
  @Test
  void sameSubcomponentUsedInDifferentHierarchies() {
    JavaFileObject parent = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface Parent {",
        "  Child1 getChild1();",
        "  Child2 getChild2();",
        "}");
    JavaFileObject child1 = JavaFileObjects.forSourceLines("test.Child1",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = LongModule.class)",
        "interface Child1 {",
        "  RepeatedSub getSub();",
        "}");
    JavaFileObject child2 = JavaFileObjects.forSourceLines("test.Child2",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface Child2 {",
        "  RepeatedSub getSub();",
        "}");
    JavaFileObject repeatedSub = JavaFileObjects.forSourceLines("test.RepeatedSub",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface RepeatedSub {",
        "  Foo getFoo();",
        "}");
    JavaFileObject injectable = JavaFileObjects.forSourceLines("test.Foo",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class Foo {",
        "  @Inject Foo(Long value) {}",
        "}");
    JavaFileObject module = JavaFileObjects.forSourceLines("test.LongModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "interface LongModule {",
        "  @Provides static Long provideLong() {",
        "    return 0L;",
        "  }",
        "}");
    Compilation compilation = daggerCompiler().compile(
        parent, child1, child2, repeatedSub, injectable, module);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("Long cannot be provided without an @Inject constructor")
        .inFile(parent)
        .onLineContaining("interface Parent");
  }
}
