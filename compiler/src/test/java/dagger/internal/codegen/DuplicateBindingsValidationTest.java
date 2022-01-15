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
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.TestUtils.message;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DuplicateBindingsValidationTest {

  @Test
  void duplicateExplicitBindings_ProvidesAndComponentProvision() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.Outer",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "final class Outer {",
        "  interface A {}",
        "",
        "  interface B {}",
        "",
        "  @Module",
        "  static class AModule {",
        "    @Provides String provideString() { return \"\"; }",
        "    @Provides A provideA(String s) { return new A() {}; }",
        "  }",
        "",
        "  @Component(modules = AModule.class)",
        "  interface Parent {",
        "    A getA();",
        "  }",
        "",
        "  @Module",
        "  static class BModule {",
        "    @Provides B provideB(A a) { return new B() {}; }",
        "  }",
        "",
        "  @Component(dependencies = Parent.class, modules = { BModule.class, AModule.class})",
        "  interface Child {",
        "    B getB();",
        "  }",
        "}");

    Compilation compilation =
        compilerWithOptions(
            fullBindingGraphValidationOption(false))
            .compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Outer.A is bound multiple times:",
                "    @Provides Outer.A Outer.AModule.provideA(String)",
                "    Outer.A Outer.Parent.getA()"))
        .inFile(component)
        .onLineContaining("interface Child");
  }

  @ValueSource(booleans = {true, false})
  @ParameterizedTest
  void duplicateExplicitBindings_TwoProvidesMethods(boolean fullBindingGraphValidation) {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import jakarta.inject.Inject;",
            "",
            "final class Outer {",
            "  interface A {}",
            "",
            "  static class B {",
            "    @Inject B(A a) {}",
            "  }",
            "",
            "  @Module",
            "  static class Module1 {",
            "    @Provides A provideA1() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module2 {",
            "    @Provides String provideString() { return \"\"; }",
            "    @Provides A provideA2(String s) { return new A() {}; }",
            "  }",
            "",
            "  @Module(includes = { Module1.class, Module2.class})",
            "  abstract static class Module3 {}",
            "",
            "  @Component(modules = { Module1.class, Module2.class})",
            "  interface TestComponent {",
            "    A getA();",
            "    B getB();",
            "  }",
            "}");

    Compilation compilation =
        compilerWithOptions(
            fullBindingGraphValidationOption(fullBindingGraphValidation))
            .compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Outer.A is bound multiple times:",
                "    @Provides Outer.A Outer.Module1.provideA1()",
                "    @Provides Outer.A Outer.Module2.provideA2(String)"))
        .inFile(component)
        .onLineContaining("interface TestComponent");

    if (fullBindingGraphValidation) {
      assertThat(compilation)
          .hadErrorContaining(
              message(
                  "Outer.A is bound multiple times:",
                  "    @Provides Outer.A Outer.Module1.provideA1()",
                  "    @Provides Outer.A Outer.Module2.provideA2(String)"))
          .inFile(component)
          .onLineContaining("class Module3");
    }

    // The duplicate bindngs are also requested from B, but we don't want to report them again.
    assertThat(compilation).hadErrorCount(fullBindingGraphValidation ? 2 : 1);
  }

  @ValueSource(booleans = {true, false})
  @ParameterizedTest
  void duplicateExplicitBindings_ProvidesVsBinds(boolean fullBindingGraphValidation) {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import jakarta.inject.Inject;",
            "",
            "final class Outer {",
            "  interface A {}",
            "",
            "  static final class B implements A {",
            "    @Inject B() {}",
            "  }",
            "",
            "  @Module",
            "  static class Module1 {",
            "    @Provides A provideA1() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static abstract class Module2 {",
            "    @Binds abstract A bindA2(B b);",
            "  }",
            "",
            "  @Module(includes = { Module1.class, Module2.class})",
            "  abstract static class Module3 {}",
            "",
            "  @Component(modules = { Module1.class, Module2.class})",
            "  interface TestComponent {",
            "    A getA();",
            "  }",
            "}");

    Compilation compilation =
        compilerWithOptions(
            fullBindingGraphValidationOption(fullBindingGraphValidation))
            .compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Outer.A is bound multiple times:",
                "    @Provides Outer.A Outer.Module1.provideA1()",
                "    @Binds Outer.A Outer.Module2.bindA2(Outer.B)"))
        .inFile(component)
        .onLineContaining("interface TestComponent");

    if (fullBindingGraphValidation) {
      assertThat(compilation)
          .hadErrorContaining(
              message(
                  "Outer.A is bound multiple times:",
                  "    @Provides Outer.A Outer.Module1.provideA1()",
                  "    @Binds Outer.A Outer.Module2.bindA2(Outer.B)"))
          .inFile(component)
          .onLineContaining("class Module3");
    }
  }

  @ValueSource(booleans = {true, false})
  @ParameterizedTest
  void duplicateBindings_TruncateAfterLimit(boolean fullBindingGraphValidation) {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import jakarta.inject.Inject;",
            "",
            "final class Outer {",
            "  interface A {}",
            "",
            "  @Module",
            "  static class Module01 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module02 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module03 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module04 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module05 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module06 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module07 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module08 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module09 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module10 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module11 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module12 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module(includes = {",
            "    Module01.class,",
            "    Module02.class,",
            "    Module03.class,",
            "    Module04.class,",
            "    Module05.class,",
            "    Module06.class,",
            "    Module07.class,",
            "    Module08.class,",
            "    Module09.class,",
            "    Module10.class,",
            "    Module11.class,",
            "    Module12.class",
            "  })",
            "  abstract static class Modules {}",
            "",
            "  @Component(modules = {",
            "    Module01.class,",
            "    Module02.class,",
            "    Module03.class,",
            "    Module04.class,",
            "    Module05.class,",
            "    Module06.class,",
            "    Module07.class,",
            "    Module08.class,",
            "    Module09.class,",
            "    Module10.class,",
            "    Module11.class,",
            "    Module12.class",
            "  })",
            "  interface TestComponent {",
            "    A getA();",
            "  }",
            "}");

    Compilation compilation =
        compilerWithOptions(
            fullBindingGraphValidationOption(fullBindingGraphValidation))
            .compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Outer.A is bound multiple times:",
                "    @Provides Outer.A Outer.Module01.provideA()",
                "    @Provides Outer.A Outer.Module02.provideA()",
                "    @Provides Outer.A Outer.Module03.provideA()",
                "    @Provides Outer.A Outer.Module04.provideA()",
                "    @Provides Outer.A Outer.Module05.provideA()",
                "    @Provides Outer.A Outer.Module06.provideA()",
                "    @Provides Outer.A Outer.Module07.provideA()",
                "    @Provides Outer.A Outer.Module08.provideA()",
                "    @Provides Outer.A Outer.Module09.provideA()",
                "    @Provides Outer.A Outer.Module10.provideA()",
                "    and 2 others"))
        .inFile(component)
        .onLineContaining(fullBindingGraphValidation ? "class Modules" : "interface TestComponent");
  }

  @ValueSource(booleans = {true, false})
  @ParameterizedTest
  void childBindingConflictsWithParent(boolean fullBindingGraphValidation) {
    JavaFileObject aComponent =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Component(modules = A.AModule.class)",
            "interface A {",
            "  Object conflict();",
            "",
            "  B.Builder b();",
            "",
            "  @Module(subcomponents = B.class)",
            "  static class AModule {",
            "    @Provides static Object abConflict() {",
            "      return \"a\";",
            "    }",
            "  }",
            "}");
    JavaFileObject bComponent =
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = B.BModule.class)",
            "interface B {",
            "  Object conflict();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    B build();",
            "  }",
            "",
            "  @Module",
            "  static class BModule {",
            "    @Provides static Object abConflict() {",
            "      return \"b\";",
            "    }",
            "  }",
            "}");

    Compilation compilation =
        compilerWithOptions(
            fullBindingGraphValidationOption(fullBindingGraphValidation))
            .compile(aComponent, bComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Object is bound multiple times:",
                "    @Provides Object test.A.AModule.abConflict()",
                "    @Provides Object test.B.BModule.abConflict()"))
        .inFile(aComponent)
        .onLineContaining(fullBindingGraphValidation ? "class AModule" : "interface A {");
  }

  @ValueSource(booleans = {true, false})
  @ParameterizedTest
  void grandchildBindingConflictsWithGrandparent(boolean fullBindingGraphValidation) {
    JavaFileObject aComponent =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Component(modules = A.AModule.class)",
            "interface A {",
            "  Object conflict();",
            "",
            "  B.Builder b();",
            "",
            "  @Module(subcomponents = B.class)",
            "  static class AModule {",
            "    @Provides static Object acConflict() {",
            "      return \"a\";",
            "    }",
            "  }",
            "}");
    JavaFileObject bComponent =
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface B {",
            "  C.Builder c();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    B build();",
            "  }",
            "}");
    JavaFileObject cComponent =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = C.CModule.class)",
            "interface C {",
            "  Object conflict();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    C build();",
            "  }",
            "",
            "  @Module",
            "  static class CModule {",
            "    @Provides static Object acConflict() {",
            "      return \"c\";",
            "    }",
            "  }",
            "}");

    Compilation compilation =
        compilerWithOptions(
            fullBindingGraphValidationOption(fullBindingGraphValidation))
            .compile(aComponent, bComponent, cComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Object is bound multiple times:",
                "    @Provides Object test.A.AModule.acConflict()",
                "    @Provides Object test.C.CModule.acConflict()"))
        .inFile(aComponent)
        .onLineContaining(fullBindingGraphValidation ? "class AModule" : "interface A {");
  }

  @ValueSource(booleans = {true, false})
  @ParameterizedTest
  void grandchildBindingConflictsWithChild(boolean fullBindingGraphValidation) {
    JavaFileObject aComponent =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface A {",
            "  B b();",
            "}");
    JavaFileObject bComponent =
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = B.BModule.class)",
            "interface B {",
            "  Object conflict();",
            "",
            "  C.Builder c();",
            "",
            "  @Module(subcomponents = C.class)",
            "  static class BModule {",
            "    @Provides static Object bcConflict() {",
            "      return \"b\";",
            "    }",
            "  }",
            "}");
    JavaFileObject cComponent =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = C.CModule.class)",
            "interface C {",
            "  Object conflict();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    C build();",
            "  }",
            "",
            "  @Module",
            "  static class CModule {",
            "    @Provides static Object bcConflict() {",
            "      return \"c\";",
            "    }",
            "  }",
            "}");

    Compilation compilation =
        compilerWithOptions(
            fullBindingGraphValidationOption(fullBindingGraphValidation))
            .compile(aComponent, bComponent, cComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Object is bound multiple times:",
                "    @Provides Object test.B.BModule.bcConflict()",
                "    @Provides Object test.C.CModule.bcConflict()"))
        .inFile(fullBindingGraphValidation ? bComponent : aComponent)
        .onLineContaining(fullBindingGraphValidation ? "class BModule" : "interface A {");
  }

  @ValueSource(booleans = {true, false})
  @ParameterizedTest
  void grandchildBindingConflictsWithParentWithNullableViolationAsWarning(boolean fullBindingGraphValidation) {
    JavaFileObject parentConflictsWithChild =
        JavaFileObjects.forSourceLines(
            "test.ParentConflictsWithChild",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.annotation.Nullable;",
            "",
            "@Component(modules = ParentConflictsWithChild.ParentModule.class)",
            "interface ParentConflictsWithChild {",
            "  Child.Builder child();",
            "",
            "  @Module(subcomponents = Child.class)",
            "  static class ParentModule {",
            "    @Provides @Nullable static Object nullableParentChildConflict() {",
            "      return \"parent\";",
            "    }",
            "  }",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child.ChildModule.class)",
            "interface Child {",
            "  Object parentChildConflictThatViolatesNullability();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Child build();",
            "  }",
            "",
            "  @Module",
            "  static class ChildModule {",
            "    @Provides static Object nonNullableParentChildConflict() {",
            "      return \"child\";",
            "    }",
            "  }",
            "}");

    Compilation compilation =
        compilerWithOptions(
            "-Adagger.nullableValidation=WARNING",
            fullBindingGraphValidationOption(fullBindingGraphValidation))
            .compile(parentConflictsWithChild, child);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Object is bound multiple times:",
                "    @Provides Object Child.ChildModule.nonNullableParentChildConflict()",
                "    @Provides @Nullable Object"
                    + " ParentConflictsWithChild.ParentModule.nullableParentChildConflict()"))
        .inFile(parentConflictsWithChild)
        .onLineContaining(
            fullBindingGraphValidation
                ? "class ParentModule"
                : "interface ParentConflictsWithChild");
  }

  private String fullBindingGraphValidationOption(boolean fullBindingGraphValidation) {
    return "-Adagger.fullBindingGraphValidation=" + (fullBindingGraphValidation ? "ERROR" : "NONE");
  }

  @Test
  void reportedInParentAndChild() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child.Builder childBuilder();",
            "  String duplicated();",
            "}");
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Optional;",
            "",
            "@Module",
            "interface ParentModule {",
            "  @Provides static String one(Optional<Object> optional) { return \"one\"; }",
            "  @Provides static String two() { return \"two\"; }",
            "  @BindsOptionalOf Object optional();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  String duplicated();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Child build();",
            "  }",
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
            "interface ChildModule {",
            "  @Provides static Object object() { return \"object\"; }",
            "}");
    Compilation compilation = daggerCompiler().compile(parent, parentModule, child, childModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("String is bound multiple times")
        .inFile(parent)
        .onLineContaining("interface Parent");
    assertThat(compilation).hadErrorCount(1);
  }
}
