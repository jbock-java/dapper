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
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static io.jbock.testing.compile.CompilationSubject.assertThat;

import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SubcomponentValidationTest {

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void factoryMethod_missingModulesWithParameters(CompilerMode compilerMode) {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent();",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = ModuleWithParameters.class)",
        "interface ChildComponent {",
        "  Object object();",
        "}");
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.ModuleWithParameters",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class ModuleWithParameters {",
        "  private final Object object;",
        "",
        "  ModuleWithParameters(Object object) {",
        "    this.object = object;",
        "  }",
        "",
        "  @Provides Object object() {",
        "    return object;",
        "  }",
        "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(componentFile, childComponentFile, moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.ChildComponent requires modules which have no visible default constructors. "
                + "Add the following modules as parameters to this method: "
                + "test.ModuleWithParameters")
        .inFile(componentFile)
        .onLineContaining("ChildComponent newChildComponent();");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void factoryMethod_grandchild(CompilerMode compilerMode) {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  ChildComponent newChildComponent();",
            "}");
    JavaFileObject childComponent =
        JavaFileObjects.forSourceLines(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface ChildComponent {",
            "  GrandchildComponent newGrandchildComponent();",
            "}");
    JavaFileObject grandchildComponent =
        JavaFileObjects.forSourceLines(
            "test.GrandchildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GrandchildModule.class)",
            "interface GrandchildComponent {",
            "  Object object();",
            "}");
    JavaFileObject grandchildModule =
        JavaFileObjects.forSourceLines(
            "test.GrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class GrandchildModule {",
            "  private final Object object;",
            "",
            "  GrandchildModule(Object object) {",
            "    this.object = object;",
            "  }",
            "",
            "  @Provides Object object() {",
            "    return object;",
            "  }",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(component, childComponent, grandchildComponent, grandchildModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "[ChildComponent.newGrandchildComponent()] "
                + "GrandchildComponent requires modules which have no visible default "
                + "constructors. Add the following modules as parameters to this method: "
                + "GrandchildModule")
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void factoryMethod_nonModuleParameter(CompilerMode compilerMode) {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent(String someRandomString);",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Subcomponent factory methods may only accept modules, but java.lang.String is not.")
        .inFile(componentFile)
        .onLine(7)
        .atColumn(43);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void factoryMethod_duplicateParameter(CompilerMode compilerMode) {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class TestModule {}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent(TestModule testModule1, TestModule testModule2);",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = TestModule.class)",
        "interface ChildComponent {}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(moduleFile, componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "A module may only occur once as an argument in a Subcomponent factory method, "
                + "but test.TestModule was already passed.")
        .inFile(componentFile)
        .onLine(7)
        .atColumn(71);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void factoryMethod_superflouousModule(CompilerMode compilerMode) {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class TestModule {}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent(TestModule testModule);",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(moduleFile, componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.TestModule is present as an argument to the test.ChildComponent factory method, "
                + "but is not one of the modules used to implement the subcomponent.")
        .inFile(componentFile)
        .onLine(7);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void missingBinding(CompilerMode compilerMode) {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides String provideString(int i) {",
        "    return Integer.toString(i);",
        "  }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent();",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = TestModule.class)",
        "interface ChildComponent {",
        "  String string();",
        "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(moduleFile, componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Integer cannot be provided without an @Inject constructor or an "
                + "@Provides-annotated method")
        .inFile(componentFile)
        .onLineContaining("interface TestComponent");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void subcomponentOnConcreteType(CompilerMode compilerMode) {
    JavaFileObject subcomponentFile = JavaFileObjects.forSourceLines("test.NotASubcomponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "final class NotASubcomponent {}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(subcomponentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void scopeMismatch(CompilerMode compilerMode) {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import jakarta.inject.Singleton;",
        "",
        "@Component",
        "@Singleton",
        "interface ParentComponent {",
        "  ChildComponent childComponent();",
        "}");
    JavaFileObject subcomponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = ChildModule.class)",
        "interface ChildComponent {",
        "  Object object();",
        "}");
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.ChildModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import jakarta.inject.Singleton;",
        "",
        "@Module",
        "final class ChildModule {",
        "  @Provides @Singleton Object provideObject() { return null; }",
        "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(componentFile, subcomponentFile, moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("@Singleton");
  }

  @Disabled
  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void delegateFactoryNotCreatedForSubcomponentWhenProviderExistsInParent(CompilerMode compilerMode) {
    JavaFileObject parentComponentFile =
        JavaFileObjects.forSourceLines(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface ParentComponent {",
            "  ChildComponent childComponent();",
            "  Dep1 dep1();",
            "  Dep2 dep2();",
            "}");
    JavaFileObject childComponentFile =
        JavaFileObjects.forSourceLines(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface ChildComponent {",
            "  Object object();",
            "}");
    JavaFileObject childModuleFile =
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class ChildModule {",
            "  @Provides Object provideObject(A a) { return null; }",
            "}");
    JavaFileObject aFile =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class A {",
            "  @Inject public A(NeedsDep1 a, Dep1 b, Dep2 c) { }",
            "}");
    JavaFileObject needsDep1File =
        JavaFileObjects.forSourceLines(
            "test.NeedsDep1",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class NeedsDep1 {",
            "  @Inject public NeedsDep1(Dep1 d) { }",
            "}");
    JavaFileObject dep1File =
        JavaFileObjects.forSourceLines(
            "test.Dep1",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "final class Dep1 {",
            "  @Inject public Dep1() { }",
            "}");
    JavaFileObject dep2File =
        JavaFileObjects.forSourceLines(
            "test.Dep2",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "final class Dep2 {",
            "  @Inject public Dep2() { }",
            "}");

    String[] generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerParentComponent")
            .addLines(
                "package test;",
                "")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines(
                "final class DaggerParentComponent implements ParentComponent {",
                "  private Provider<Dep1> dep1Provider;",
                "  private Provider<Dep2> dep2Provider;")
            .addLines(
                "  @Override",
                "  public Dep1 dep1() {",
                "    return dep1Provider.get();",
                "  }",
                "",
                "  @Override",
                "  public Dep2 dep2() {",
                "    return dep2Provider.get();",
                "  }",
                "",
                "  @Override",
                "  public ChildComponent childComponent() {",
                "    return new ChildComponentImpl(parentComponent);",
                "  }")
            .addLinesIn(
                DEFAULT_MODE,
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.dep1Provider = DoubleCheck.provider(Dep1_Factory.create());",
                "    this.dep2Provider = DoubleCheck.provider(Dep2_Factory.create());",
                "  }",
                "")
            .addLinesIn(
                FAST_INIT_MODE,
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.dep1Provider = DoubleCheck.provider(new SwitchingProvider<Dep1>(parentComponent, 0));",
                "    this.dep2Provider = DoubleCheck.provider(new SwitchingProvider<Dep2>(parentComponent, 1));",
                "  }")
            .addLines(
                "  private static final class ChildComponentImpl implements ChildComponent {",
                "    private NeedsDep1 needsDep1() {",
                "      return new NeedsDep1(parentComponent.dep1Provider.get());",
                "    }",
                "",
                "    private A a() {",
                "      return new A(needsDep1(), parentComponent.dep1Provider.get(), parentComponent.dep2Provider.get());",
                "    }",
                "",
                "    @Override",
                "    public Object object() {",
                "      return ChildModule_ProvideObjectFactory.provideObject(childModule, a());",
                "    }",
                "  }")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private static final class SwitchingProvider<T> implements Provider<T> {",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0: // test.Dep1 ",
                "        return (T) new Dep1();",
                "        case 1: // test.Dep2 ",
                "        return (T) new Dep2();",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }",
                "  }",
                "}")
            .lines();

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(
                parentComponentFile,
                childComponentFile,
                childModuleFile,
                aFile,
                needsDep1File,
                dep1File,
                dep2File);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParentComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void multipleSubcomponentsWithSameSimpleNamesCanExistInSameComponent(CompilerMode compilerMode) {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  Foo.Sub newInstanceSubcomponent();",
            "  NoConflict newNoConflictSubcomponent();",
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "interface Foo {",
            "  @Subcomponent interface Sub {",
            "    Bar.Sub newBarSubcomponent();",
            "  }",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "interface Bar {",
            "  @Subcomponent interface Sub {",
            "    test.subpackage.Sub newSubcomponentInSubpackage();",
            "  }",
            "}");
    JavaFileObject baz =
        JavaFileObjects.forSourceLines(
            "test.subpackage.Sub",
            "package test.subpackage;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent public interface Sub {}");
    JavaFileObject noConflict =
        JavaFileObjects.forSourceLines(
            "test.NoConflict",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent interface NoConflict {}");

    List<String> componentGeneratedFile = new ArrayList<>();
    Collections.addAll(componentGeneratedFile,
        "package test;",
        "",
        "import test.subpackage.Sub;");
    Collections.addAll(componentGeneratedFile,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(componentGeneratedFile,
        "final class DaggerParentComponent implements ParentComponent {",
        "  @Override",
        "  public Foo.Sub newInstanceSubcomponent() {",
        "    return new F_SubImpl(parentComponent);",
        "  }",
        "",
        "  @Override",
        "  public NoConflict newNoConflictSubcomponent() {",
        "    return new NoConflictImpl(parentComponent);",
        "  }",
        "",
        "  static final class Builder {",
        "    public ParentComponent build() {",
        "      return new DaggerParentComponent();",
        "    }",
        "  }",
        "",
        "  private static final class ts_SubImpl implements Sub {",
        "  }",
        "",
        "  private static final class B_SubImpl implements Bar.Sub {",
        "  }",
        "",
        "  private static final class F_SubImpl implements Foo.Sub {",
        "  }",
        "",
        "  private static final class NoConflictImpl implements NoConflict {",
        "  }",
        "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(parent, foo, bar, baz, noConflict);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParentComponent")
        .containsLines(componentGeneratedFile);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void subcomponentSimpleNamesDisambiguated(CompilerMode compilerMode) {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  Sub newSubcomponent();",
            "}");
    JavaFileObject sub =
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent interface Sub {",
            "  test.deep.many.levels.that.match.test.Sub newDeepSubcomponent();",
            "}");
    JavaFileObject deepSub =
        JavaFileObjects.forSourceLines(
            "test.deep.many.levels.that.match.test.Sub",
            "package test.deep.many.levels.that.match.test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent public interface Sub {}");

    List<String> componentGeneratedFile = new ArrayList<>();
    Collections.addAll(componentGeneratedFile,
        "package test;");
    Collections.addAll(componentGeneratedFile,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(componentGeneratedFile,
        "final class DaggerParentComponent implements ParentComponent {",
        "  @Override",
        "  public Sub newSubcomponent() {",
        "    return new t_SubImpl(parentComponent);",
        "  }",
        "",
        "  static final class Builder {",
        "    public ParentComponent build() {",
        "      return new DaggerParentComponent();",
        "    }",
        "  }",
        "",
        "  private static final class tdmltmt_SubImpl implements test.deep.many.levels.that.match.test.Sub {",
        "  }",
        "",
        "  private static final class t_SubImpl implements Sub {",
        "  }",
        "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(parent, sub, deepSub);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParentComponent")
        .containsLines(componentGeneratedFile);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void subcomponentSimpleNamesDisambiguatedInRoot(CompilerMode compilerMode) {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "ParentComponent",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  Sub newSubcomponent();",
            "}");
    JavaFileObject sub =
        JavaFileObjects.forSourceLines(
            "Sub",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent interface Sub {",
            "  test.deep.many.levels.that.match.test.Sub newDeepSubcomponent();",
            "}");
    JavaFileObject deepSub =
        JavaFileObjects.forSourceLines(
            "test.deep.many.levels.that.match.test.Sub",
            "package test.deep.many.levels.that.match.test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent public interface Sub {}");

    List<String> componentGeneratedFile = new ArrayList<>();
    Collections.addAll(componentGeneratedFile,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(componentGeneratedFile,
        "final class DaggerParentComponent implements ParentComponent {",
        "  @Override",
        "  public Sub newSubcomponent() {",
        "    return new $_SubImpl(parentComponent);",
        "  }",
        "",
        "  private static final class tdmltmt_SubImpl implements test.deep.many.levels.that.match.test.Sub {",
        "  }",
        "",
        "  private static final class $_SubImpl implements Sub {",
        "  }",
        "}",
        "");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(parent, sub, deepSub);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("DaggerParentComponent")
        .containsLines(componentGeneratedFile);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void subcomponentImplNameUsesFullyQualifiedClassNameIfNecessary(CompilerMode compilerMode) {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  top1.a.b.c.d.E.F.Sub top1();",
            "  top2.a.b.c.d.E.F.Sub top2();",
            "}");
    JavaFileObject top1 =
        JavaFileObjects.forSourceLines(
            "top1.a.b.c.d.E",
            "package top1.a.b.c.d;",
            "",
            "import dagger.Subcomponent;",
            "",
            "public interface E {",
            "  interface F {",
            "    @Subcomponent interface Sub {}",
            "  }",
            "}");
    JavaFileObject top2 =
        JavaFileObjects.forSourceLines(
            "top2.a.b.c.d.E",
            "package top2.a.b.c.d;",
            "",
            "import dagger.Subcomponent;",
            "",
            "public interface E {",
            "  interface F {",
            "    @Subcomponent interface Sub {}",
            "  }",
            "}");

    List<String> componentGeneratedFile = new ArrayList<>();
    Collections.addAll(componentGeneratedFile,
        "package test;",
        "",
        "import top1.a.b.c.d.E;");
    Collections.addAll(componentGeneratedFile,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(componentGeneratedFile,
        "final class DaggerParentComponent implements ParentComponent {",
        "  @Override",
        "  public E.F.Sub top1() {",
        "    return new F_SubImpl(parentComponent);",
        "  }",
        "",
        "  @Override",
        "  public top2.a.b.c.d.E.F.Sub top2() {",
        "    return new F2_SubImpl(parentComponent);",
        "  }",
        "",
        "  private static final class F_SubImpl implements E.F.Sub {",
        "    private F_SubImpl(DaggerParentComponent parentComponent) {",
        "      this.parentComponent = parentComponent;",
        "    }",
        "  }",
        "  private static final class F2_SubImpl implements top2.a.b.c.d.E.F.Sub {",
        "    private F2_SubImpl(DaggerParentComponent parentComponent) {",
        "      this.parentComponent = parentComponent;",
        "    }",
        "  }",
        "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(parent, top1, top2);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParentComponent")
        .containsLines(componentGeneratedFile);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void parentComponentNameShouldNotBeDisambiguatedWhenItConflictsWithASubcomponent(CompilerMode compilerMode) {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface C {",
            "  test.Foo.C newInstanceC();",
            "}");
    JavaFileObject subcomponentWithSameSimpleNameAsParent =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "interface Foo {",
            "  @Subcomponent interface C {}",
            "}");

    List<String> componentGeneratedFile = new ArrayList<>();
    Collections.addAll(componentGeneratedFile,
        "package test;",
        "");
    Collections.addAll(componentGeneratedFile,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(componentGeneratedFile,
        "final class DaggerC implements C {",
        "  @Override",
        "  public Foo.C newInstanceC() {",
        "    return new F_CImpl(c);",
        "  }",
        "",
        "  private static final class F_CImpl implements Foo.C {",
        "  }",
        "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(parent, subcomponentWithSameSimpleNameAsParent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerC")
        .containsLines(componentGeneratedFile);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void subcomponentBuilderNamesShouldNotConflict(CompilerMode compilerMode) {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Subcomponent;",
            "",
            "@Component",
            "interface C {",
            "  Foo.Sub.Builder fooBuilder();",
            "  Bar.Sub.Builder barBuilder();",
            "",
            "  interface Foo {",
            "    @Subcomponent",
            "    interface Sub {",
            "      @Subcomponent.Builder",
            "      interface Builder {",
            "        Sub build();",
            "      }",
            "    }",
            "  }",
            "",
            "  interface Bar {",
            "    @Subcomponent",
            "    interface Sub {",
            "      @Subcomponent.Builder",
            "      interface Builder {",
            "        Sub build();",
            "      }",
            "    }",
            "  }",
            "}");
    List<String> componentGeneratedFile = new ArrayList<>();
    Collections.addAll(componentGeneratedFile,
        "package test;");
    Collections.addAll(componentGeneratedFile,
        GeneratedLines.generatedImports());
    Collections.addAll(componentGeneratedFile,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(componentGeneratedFile,
        "final class DaggerC implements C {",
        "  @Override",
        "  public C.Foo.Sub.Builder fooBuilder() {",
        "    return new F_SubBuilder(c);",
        "  }",
        "",
        "  @Override",
        "  public C.Bar.Sub.Builder barBuilder() {",
        "    return new B_SubBuilder(c);",
        "  }",
        "",
        // TODO(bcorso): Reverse the order of subcomponent and builder so that subcomponent
        // comes first.
        "  private static final class F_SubBuilder implements C.Foo.Sub.Builder {",
        "    @Override",
        "    public C.Foo.Sub build() {",
        "      return new F_SubImpl(c);",
        "    }",
        "  }",
        "",
        "  private static final class B_SubBuilder implements C.Bar.Sub.Builder {",
        "    @Override",
        "    public C.Bar.Sub build() {",
        "      return new B_SubImpl(c);",
        "    }",
        "  }",
        "",
        "  private static final class F_SubImpl implements C.Foo.Sub {",
        "    private F_SubImpl(DaggerC c) {",
        "      this.c = c;",
        "    }",
        "  }",
        "",
        "  private static final class B_SubImpl implements C.Bar.Sub {",
        "    private B_SubImpl(DaggerC c) {",
        "      this.c = c;",
        "    }",
        "  }",
        "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(parent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerC")
        .containsLines(componentGeneratedFile);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void duplicateBindingWithSubcomponentDeclaration(CompilerMode compilerMode) {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module(subcomponents = Sub.class)",
            "class TestModule {",
            "  @Provides Sub.Builder providesConflictsWithModuleSubcomponents() { return null; }",
            "  @Provides Object usesSubcomponentBuilder(Sub.Builder builder) {",
            "    return new Builder().toString();",
            "  }",
            "}");

    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Sub {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Sub build();",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface C {",
            "  Object dependsOnBuilder();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(module, component, subcomponent);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("Sub.Builder is bound multiple times:");
    assertThat(compilation)
        .hadErrorContaining(
            "@Provides Sub.Builder "
                + "TestModule.providesConflictsWithModuleSubcomponents()");
    assertThat(compilation)
        .hadErrorContaining("@Module(subcomponents = Sub.class) for TestModule");
  }

  @Test
  void subcomponentDependsOnGeneratedType() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Child.Builder childBuilder();",
            "}");

    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child extends ChildSupertype {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Child build();",
            "  }",
            "}");

    JavaFileObject childSupertype =
        JavaFileObjects.forSourceLines(
            "test.ChildSupertype",
            "package test;",
            "",
            "interface ChildSupertype {",
            "  GeneratedType generatedType();",
            "}");

    Compilation compilation =
        daggerCompiler(
            new GeneratingProcessor(
                "test.GeneratedType",
                "package test;",
                "",
                "import jakarta.inject.Inject;",
                "",
                "final class GeneratedType {",
                "  @Inject GeneratedType() {}",
                "}"))
            .compile(parent, child, childSupertype);
    assertThat(compilation).succeededWithoutWarnings();
  }
}
