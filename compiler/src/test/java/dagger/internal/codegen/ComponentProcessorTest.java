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

import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static io.jbock.testing.compile.CompilationSubject.assertThat;

import dagger.internal.codegen.base.Util;
import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ComponentProcessorTest {

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void doubleBindingFromResolvedModules(CompilerMode compilerMode) {
    JavaFileObject parent = JavaFileObjects.forSourceLines("test.ParentModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.util.List;",
        "",
        "@Module",
        "abstract class ParentModule<A> {",
        "  @Provides List<A> provideListB(A a) { return null; }",
        "}");
    JavaFileObject child = JavaFileObjects.forSourceLines("test.ChildModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "class ChildNumberModule extends ParentModule<Integer> {",
        "  @Provides Integer provideInteger() { return null; }",
        "}");
    JavaFileObject another = JavaFileObjects.forSourceLines("test.AnotherModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.util.List;",
        "",
        "@Module",
        "class AnotherModule {",
        "  @Provides List<Integer> provideListOfInteger() { return null; }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.BadComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.List;",
        "",
        "@Component(modules = {ChildNumberModule.class, AnotherModule.class})",
        "interface BadComponent {",
        "  List<Integer> listOfInteger();",
        "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(parent, child, another, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("List<Integer> is bound multiple times");
    assertThat(compilation)
        .hadErrorContaining("@Provides List<Integer> ChildNumberModule.provideListB(Integer)");
    assertThat(compilation)
        .hadErrorContaining("@Provides List<Integer> AnotherModule.provideListOfInteger()");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void privateNestedClassWithWarningThatIsAnErrorInComponent(CompilerMode compilerMode) {
    JavaFileObject outerClass = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class OuterClass {",
        "  @Inject OuterClass(InnerClass innerClass) {}",
        "",
        "  private static final class InnerClass {",
        "    @Inject InnerClass() {}",
        "  }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.BadComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface BadComponent {",
        "  OuterClass outerClass();",
        "}");
    Compilation compilation =
        compilerWithOptions(
            Util.concat(compilerMode.javacopts(), List.of("-Adagger.privateMemberValidation=WARNING")))
            .compile(outerClass, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private classes");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void simpleComponent(CompilerMode compilerMode) {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import jakarta.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
        "  Lazy<SomeInjectableType> lazySomeInjectableType();",
        "  Provider<SomeInjectableType> someInjectableTypeProvider();",
        "}");

    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerSimpleComponent")
            .addLines(
                "package test;",
                "")
            .addLines(GeneratedLines.generatedImports(
                "import dagger.Lazy;",
                "import dagger.internal.DoubleCheck;",
                "import jakarta.inject.Provider;"))
            .addLines("")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines(
                "final class DaggerSimpleComponent implements SimpleComponent {",
                "  private final DaggerSimpleComponent simpleComponent = this;")
            .addLinesIn(
                FAST_INIT_MODE,
                "  @Override",
                "  public SomeInjectableType someInjectableType() {",
                "    return someInjectableTypeProvider.get();",
                "  }",
                "",
                "  @Override",
                "  public Lazy<SomeInjectableType> lazySomeInjectableType() {",
                "    return DoubleCheck.lazy(someInjectableTypeProvider);",
                "  }",
                "",
                "  @Override",
                "  public Provider<SomeInjectableType> someInjectableTypeProvider() {",
                "    return someInjectableTypeProvider;",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.someInjectableTypeProvider = new SwitchingProvider<>(simpleComponent, 0);",
                "  }",
                "",
                "  private static final class SwitchingProvider<T> implements Provider<T> {",
                "    private final DaggerSimpleComponent simpleComponent;",
                "",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0: // test.SomeInjectableType ",
                "        return (T) new SomeInjectableType();",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }")
            .addLinesIn(
                DEFAULT_MODE,
                "  @Override",
                "  public SomeInjectableType someInjectableType() {",
                "    return new SomeInjectableType();",
                "  }",
                "",
                "  @Override",
                "  public Lazy<SomeInjectableType> lazySomeInjectableType() {",
                "    return DoubleCheck.lazy(SomeInjectableType_Factory.create());",
                "  }",
                "",
                "  @Override",
                "  public Provider<SomeInjectableType> someInjectableTypeProvider() {",
                "    return SomeInjectableType_Factory.create();",
                "  }",
                "}")
            .build();

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void componentWithScope(CompilerMode compilerMode) {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Singleton;",
        "",
        "@Singleton",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import jakarta.inject.Provider;",
        "import jakarta.inject.Singleton;",
        "",
        "@Singleton",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
        "  Lazy<SomeInjectableType> lazySomeInjectableType();",
        "  Provider<SomeInjectableType> someInjectableTypeProvider();",
        "}");
    String[] generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerSimpleComponent")
            .addLines(
                "package test;",
                "")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines("final class DaggerSimpleComponent implements SimpleComponent {",
                "  private Provider<SomeInjectableType> someInjectableTypeProvider;")
            .addLines(
                "  @Override",
                "  public SomeInjectableType someInjectableType() {",
                "    return someInjectableTypeProvider.get();",
                "  }",
                "",
                "  @Override",
                "  public Lazy<SomeInjectableType> lazySomeInjectableType() {",
                "    return DoubleCheck.lazy(someInjectableTypeProvider);",
                "  }",
                "",
                "  @Override",
                "  public Provider<SomeInjectableType> someInjectableTypeProvider() {",
                "    return someInjectableTypeProvider;",
                "  }")
            .addLinesIn(
                FAST_INIT_MODE,
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.someInjectableTypeProvider = DoubleCheck.provider(new SwitchingProvider<SomeInjectableType>(simpleComponent, 0));",
                "  }")
            .addLinesIn(
                DEFAULT_MODE,
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.someInjectableTypeProvider = DoubleCheck.provider(SomeInjectableType_Factory.create());",
                "  }",
                "")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private static final class SwitchingProvider<T> implements Provider<T> {",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0: // test.SomeInjectableType ",
                "        return (T) new SomeInjectableType();",
                "",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }",
                "  }")
            .addLines("}")
            .lines();
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(List.of(generatedComponent));
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void componentWithModule(CompilerMode compilerMode) {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A(B b) {}",
        "}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "interface B {}");
    JavaFileObject cFile = JavaFileObjects.forSourceLines("test.C",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class C {",
        "  @Inject C() {}",
        "}");

    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides B b(C c) { return null; }",
        "}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  A a();",
        "}");

    String[] generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;",
                "")
            .addLines(GeneratedLines.generatedImports(
                "import dagger.internal.Preconditions;"))
            .addLines("")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines(
                "final class DaggerTestComponent implements TestComponent {",
                "  private final TestModule testModule;",
                "",
                "  private DaggerTestComponent(TestModule testModuleParam) {",
                "    this.testModule = testModuleParam;",
                "  }",
                "",
                "  private B b() {",
                "    return TestModule_BFactory.b(testModule, new C());",
                "  }",
                "",
                "  @Override",
                "  public A a() {",
                "    return new A(b());",
                "  }",
                "",
                "  static final class Builder {",
                "    private TestModule testModule;",
                "",
                "    public Builder testModule(TestModule testModule) {",
                "      this.testModule = Preconditions.checkNotNull(testModule);",
                "      return this;",
                "    }",
                "",
                "    public TestComponent build() {",
                "      if (testModule == null) {",
                "        this.testModule = new TestModule();",
                "      }",
                "      return new DaggerTestComponent(testModule);",
                "    }",
                "  }",
                "}")
            .lines();

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(aFile, bFile, cFile, moduleFile, componentFile);
    assertThat(compilation).succeeded();

    assertThat(compilation).generatedSourceFile("test.DaggerTestComponent")
        .containsLines(List.of(generatedComponent));
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void componentWithAbstractModule(CompilerMode compilerMode) {
    JavaFileObject aFile =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(B b) {}",
            "}");
    JavaFileObject bFile =
        JavaFileObjects.forSourceLines("test.B",
            "package test;",
            "",
            "interface B {}");
    JavaFileObject cFile =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class C {",
            "  @Inject C() {}",
            "}");

    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides static B b(C c) { return null; }",
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
            "  A a();",
            "}");

    String[] generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;",
                "")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines(
                "final class DaggerTestComponent implements TestComponent {",
                "  private B b() {",
                "    return TestModule_BFactory.b(new C());",
                "  }",
                "",
                "  @Override",
                "  public A a() {",
                "    return new A(b());",
                "  }",
                "}")
            .lines();

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(aFile, bFile, cFile, moduleFile, componentFile);
    assertThat(compilation).succeeded();

    assertThat(compilation).generatedSourceFile("test.DaggerTestComponent")
        .containsLines(List.of(generatedComponent));
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void transitiveModuleDeps(CompilerMode compilerMode) {
    JavaFileObject always = JavaFileObjects.forSourceLines("test.AlwaysIncluded",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class AlwaysIncluded {}");
    JavaFileObject testModule = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {DepModule.class, AlwaysIncluded.class})",
        "final class TestModule extends ParentTestModule {}");
    JavaFileObject parentTest = JavaFileObjects.forSourceLines("test.ParentTestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {ParentTestIncluded.class, AlwaysIncluded.class})",
        "class ParentTestModule {}");
    JavaFileObject parentTestIncluded = JavaFileObjects.forSourceLines("test.ParentTestIncluded",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = AlwaysIncluded.class)",
        "final class ParentTestIncluded {}");
    JavaFileObject depModule = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {RefByDep.class, AlwaysIncluded.class})",
        "final class DepModule extends ParentDepModule {}");
    JavaFileObject refByDep = JavaFileObjects.forSourceLines("test.RefByDep",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = AlwaysIncluded.class)",
        "final class RefByDep extends ParentDepModule {}");
    JavaFileObject parentDep = JavaFileObjects.forSourceLines("test.ParentDepModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {ParentDepIncluded.class, AlwaysIncluded.class})",
        "class ParentDepModule {}");
    JavaFileObject parentDepIncluded = JavaFileObjects.forSourceLines("test.ParentDepIncluded",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = AlwaysIncluded.class)",
        "final class ParentDepIncluded {}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "}");
    // Generated code includes all includes, but excludes the parent modules.
    // The "always" module should only be listed once.
    String[] generatedComponent = compilerMode
        .javaFileBuilder("test.DaggerTestComponent")
        .addLines(
            "package test;",
            "")
        .addLines(GeneratedLines.generatedImports("import dagger.internal.Preconditions;"))
        .addLines("")
        .addLines(GeneratedLines.generatedAnnotations())
        .addLines(
            "final class DaggerTestComponent implements TestComponent {",
            "  static final class Builder {",
            "",
            "    @Deprecated",
            "    public Builder testModule(TestModule testModule) {",
            "      Preconditions.checkNotNull(testModule);",
            "      return this;",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder parentTestIncluded(ParentTestIncluded parentTestIncluded) {",
            "      Preconditions.checkNotNull(parentTestIncluded);",
            "      return this;",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder alwaysIncluded(AlwaysIncluded alwaysIncluded) {",
            "      Preconditions.checkNotNull(alwaysIncluded);",
            "      return this;",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder depModule(DepModule depModule) {",
            "      Preconditions.checkNotNull(depModule);",
            "      return this;",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder parentDepIncluded(ParentDepIncluded parentDepIncluded) {",
            "      Preconditions.checkNotNull(parentDepIncluded);",
            "      return this;",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder refByDep(RefByDep refByDep) {",
            "      Preconditions.checkNotNull(refByDep);",
            "      return this;",
            "    }",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent();",
            "    }",
            "  }",
            "}")
        .lines();
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(
                always,
                testModule,
                parentTest,
                parentTestIncluded,
                depModule,
                refByDep,
                parentDep,
                parentDepIncluded,
                componentFile);
    assertThat(compilation).succeeded();

    assertThat(compilation).generatedSourceFile("test.DaggerTestComponent")
        .containsLines(List.of(generatedComponent));
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void generatedTransitiveModule(CompilerMode compilerMode) {
    JavaFileObject rootModule = JavaFileObjects.forSourceLines("test.RootModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = GeneratedModule.class)",
        "final class RootModule {}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = RootModule.class)",
        "interface TestComponent {}");
    assertThat(
        compilerWithOptions(compilerMode.javacopts()).compile(rootModule, component))
        .failed();
    assertThat(
        daggerCompiler(
            new GeneratingProcessor(
                "test.GeneratedModule",
                "package test;",
                "",
                "import dagger.Module;",
                "",
                "@Module",
                "final class GeneratedModule {}"))
            .compile(rootModule, component))
        .succeeded();
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void generatedModuleInSubcomponent(CompilerMode compilerMode) {
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GeneratedModule.class)",
            "interface ChildComponent {}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  ChildComponent childComponent();",
            "}");
    assertThat(
        compilerWithOptions(compilerMode.javacopts()).compile(subcomponent, component))
        .failed();
    assertThat(
        daggerCompiler(
            new GeneratingProcessor(
                "test.GeneratedModule",
                "package test;",
                "",
                "import dagger.Module;",
                "",
                "@Module",
                "final class GeneratedModule {}"))
            .compile(subcomponent, component))
        .succeeded();
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void subcomponentNotGeneratedIfNotUsedInGraph(CompilerMode compilerMode) {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  String notSubcomponent();",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module(subcomponents = Child.class)",
            "class ParentModule {",
            "  @Provides static String notSubcomponent() { return new String(); }",
            "}");

    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Child build();",
            "  }",
            "}");

    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;",
        "");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedImports("import dagger.internal.Preconditions;"));
    Collections.addAll(generatedComponent,
        "");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(generatedComponent,
        "final class DaggerParent implements Parent {",
        "  private final DaggerParent parent = this;",
        "",
        "  private DaggerParent() {",
        "",
        "",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static Parent create() {",
        "    return new Builder().build();",
        "  }",
        "",
        "  @Override",
        "  public String notSubcomponent() {",
        "    return ParentModule_NotSubcomponentFactory.notSubcomponent();",
        "  }",
        "",
        "  static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    /**",
        "     * @deprecated This module is declared, but an instance is not used in the component. This method is a no-op. For more, see https://dagger.dev/unused-modules.",
        "     */",
        "    @Deprecated",
        "    public Builder parentModule(ParentModule parentModule) {",
        "      Preconditions.checkNotNull(parentModule);",
        "      return this;",
        "    }",
        "",
        "    public Parent build() {",
        "      return new DaggerParent();",
        "    }",
        "  }",
        "}",
        "");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(component, module, subcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testDefaultPackage(CompilerMode compilerMode) {
    JavaFileObject aClass = JavaFileObjects.forSourceLines("AClass", "class AClass {}");
    JavaFileObject bClass = JavaFileObjects.forSourceLines("BClass",
        "import jakarta.inject.Inject;",
        "",
        "class BClass {",
        "  @Inject BClass(AClass a) {}",
        "}");
    JavaFileObject aModule = JavaFileObjects.forSourceLines("AModule",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module class AModule {",
        "  @Provides AClass aClass() {",
        "    return new AClass();",
        "  }",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("SomeComponent",
        "import dagger.Component;",
        "",
        "@Component(modules = AModule.class)",
        "interface SomeComponent {",
        "  BClass bClass();",
        "}");
    assertThat(
        compilerWithOptions(compilerMode.javacopts())
            .compile(aModule, aClass, bClass, component))
        .succeeded();
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void componentInjection(CompilerMode compilerMode) {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType(SimpleComponent component) {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import jakarta.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
        "  Provider<SimpleComponent> selfProvider();",
        "}");
    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerSimpleComponent")
            .addLines(
                "package test;",
                "")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines(
                "final class DaggerSimpleComponent implements SimpleComponent {",
                "  private Provider<SimpleComponent> simpleComponentProvider;",
                "  private final DaggerSimpleComponent simpleComponent = this;",
                "")
            .addLinesIn(
                DEFAULT_MODE,
                "  @Override",
                "  public SomeInjectableType someInjectableType() {",
                "    return new SomeInjectableType(this);",
                "  }")
            .addLinesIn(
                FAST_INIT_MODE,
                "  @Override",
                "  public SomeInjectableType someInjectableType() {",
                "    return new SomeInjectableType(simpleComponentProvider.get());",
                "  }")
            .addLines(
                "  @Override",
                "  public Provider<SimpleComponent> selfProvider() {",
                "    return simpleComponentProvider;",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.simpleComponentProvider = InstanceFactory.create((SimpleComponent) simpleComponent);",
                "  }",
                "}")
            .build();
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();

    assertThat(compilation).generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void componentDependency(CompilerMode compilerMode) {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A() {}",
        "}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Provider;",
        "",
        "final class B {",
        "  @Inject B(Provider<A> a) {}",
        "}");
    JavaFileObject aComponentFile = JavaFileObjects.forSourceLines("test.AComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface AComponent {",
        "  A a();",
        "}");
    JavaFileObject bComponentFile = JavaFileObjects.forSourceLines("test.AComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = AComponent.class)",
        "interface BComponent {",
        "  B b();",
        "}");
    String[] generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerBComponent")
            .addLines(
                "package test;",
                "")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines(
                "final class DaggerBComponent implements BComponent {")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private final AComponent aComponent;",
                "  private final DaggerBComponent bComponent = this;",
                "  private Provider<A> aProvider;",
                "",
                "  private DaggerBComponent(AComponent aComponentParam) {",
                "    this.aComponent = aComponentParam;",
                "    initialize(aComponentParam);",
                "  }",
                "",
                "  @Override",
                "  public B b() {",
                "    return new B(aProvider);",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(final AComponent aComponentParam) {",
                "    this.aProvider = new SwitchingProvider<>(bComponent, 0);",
                "  }")
            .addLinesIn(
                DEFAULT_MODE,
                "  private Provider<A> aProvider;",
                "",
                "  private DaggerBComponent(AComponent aComponentParam) {",
                "    initialize(aComponentParam);",
                "  }",
                "",
                "  @Override",
                "  public B b() {",
                "    return new B(aProvider);",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(final AComponent aComponentParam) {",
                "    this.aProvider = new test_AComponent_a(aComponentParam);",
                "  }")
            .addLines(
                "  static final class Builder {",
                "    private AComponent aComponent;",
                "",
                "    public Builder aComponent(AComponent aComponent) {",
                "      this.aComponent = Preconditions.checkNotNull(aComponent);",
                "      return this;",
                "    }",
                "",
                "    public BComponent build() {",
                "      Preconditions.checkBuilderRequirement(aComponent, AComponent.class);",
                "      return new DaggerBComponent(aComponent);",
                "    }",
                "  }")
            .addLinesIn(
                DEFAULT_MODE,
                "  private static final class test_AComponent_a implements Provider<A> {",
                "    private final AComponent aComponent;",
                "",
                "    test_AComponent_a(AComponent aComponent) {",
                "      this.aComponent = aComponent;",
                "    }",
                "",
                "    @Override",
                "    public A get() {",
                "      return Preconditions.checkNotNullFromComponent(aComponent.a());",
                "    }",
                "  }",
                "}")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private static final class SwitchingProvider<T> implements Provider<T> {",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0: // test.A ",
                "        return (T) Preconditions.checkNotNullFromComponent(bComponent.aComponent.a());",
                "",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }",
                "  }")
            .lines();
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(aFile, bFile, aComponentFile, bComponentFile);
    assertThat(compilation).succeeded();

    assertThat(compilation).generatedSourceFile("test.DaggerBComponent")
        .containsLines(List.of(generatedComponent));
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void moduleNameCollision(CompilerMode compilerMode) {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "public final class A {}");
    JavaFileObject otherAFile = JavaFileObjects.forSourceLines("other.test.A",
        "package other.test;",
        "",
        "public final class A {}");

    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "public final class TestModule {",
        "  @Provides A a() { return null; }",
        "}");
    JavaFileObject otherModuleFile = JavaFileObjects.forSourceLines("other.test.TestModule",
        "package other.test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "public final class TestModule {",
        "  @Provides A a() { return null; }",
        "}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = {TestModule.class, other.test.TestModule.class})",
        "interface TestComponent {",
        "  A a();",
        "  other.test.A otherA();",
        "}");
    String[] generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines("package test;",
                "")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines(
                "final class DaggerTestComponent implements TestComponent {",
                "  private final TestModule testModule;",
                "  private final other.test.TestModule testModule2;",
                "",
                "  private DaggerTestComponent(TestModule testModuleParam, other.test.TestModule testModuleParam2) {",
                "    this.testModule = testModuleParam;",
                "    this.testModule2 = testModuleParam2;",
                "  }",
                "",
                "  @Override",
                "  public A a() {",
                "    return TestModule_AFactory.a(testModule);",
                "  }",
                "",
                "  @Override",
                "  public other.test.A otherA() {",
                "    return other.test.TestModule_AFactory.a(testModule2);",
                "  }",
                "",
                "  static final class Builder {",
                "    private TestModule testModule;",
                "    private other.test.TestModule testModule2;",
                "",
                "    public Builder testModule(TestModule testModule) {",
                "      this.testModule = Preconditions.checkNotNull(testModule);",
                "      return this;",
                "    }",
                "",
                "    public Builder testModule(other.test.TestModule testModule) {",
                "      this.testModule2 = Preconditions.checkNotNull(testModule);",
                "      return this;",
                "    }",
                "",
                "    public TestComponent build() {",
                "      if (testModule == null) {",
                "        this.testModule = new TestModule();",
                "      }",
                "      if (testModule2 == null) {",
                "        this.testModule2 = new other.test.TestModule();",
                "      }",
                "      return new DaggerTestComponent(testModule, testModule2);",
                "    }",
                "  }",
                "}")
            .lines();
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(aFile, otherAFile, moduleFile, otherModuleFile, componentFile);
    assertThat(compilation).succeeded();

    assertThat(compilation).generatedSourceFile("test.DaggerTestComponent")
        .containsLines(List.of(generatedComponent));
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void ignoresDependencyMethodsFromObject(CompilerMode compilerMode) {
    JavaFileObject injectedTypeFile =
        JavaFileObjects.forSourceLines(
            "test.InjectedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class InjectedType {",
            "  @Inject InjectedType(",
            "      String stringInjection,",
            "      int intInjection,",
            "      AComponent aComponent,",
            "      Class<AComponent> aClass) {}",
            "}");
    JavaFileObject aComponentFile =
        JavaFileObjects.forSourceLines(
            "test.AComponent",
            "package test;",
            "",
            "class AComponent {",
            "  String someStringInjection() {",
            "    return \"injectedString\";",
            "  }",
            "",
            "  int someIntInjection() {",
            "    return 123;",
            "  }",
            "",
            "  Class<AComponent> someClassInjection() {",
            "    return AComponent.class;",
            "  }",
            "",
            "  @Override",
            "  public String toString() {",
            "    return null;",
            "  }",
            "",
            "  @Override",
            "  public int hashCode() {",
            "    return 456;",
            "  }",
            "",
            "  @Override",
            "  public AComponent clone() {",
            "    return null;",
            "  }",
            "}");
    JavaFileObject bComponentFile =
        JavaFileObjects.forSourceLines(
            "test.AComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = AComponent.class)",
            "interface BComponent {",
            "  InjectedType injectedType();",
            "}");

    String[] generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines("package test;",
                "")
            .addLines(GeneratedLines.generatedImports("import dagger.internal.Preconditions;"))
            .addLines("")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines(
                "final class DaggerBComponent implements BComponent {",
                "  private final AComponent aComponent;",
                "",
                "  private DaggerBComponent(AComponent aComponentParam) {",
                "    this.aComponent = aComponentParam;",
                "  }",
                "",
                "  @Override",
                "  public InjectedType injectedType() {",
                "    return new InjectedType(Preconditions.checkNotNullFromComponent(aComponent.someStringInjection()), aComponent.someIntInjection(), aComponent, Preconditions.checkNotNullFromComponent(aComponent.someClassInjection()));",
                "  }",
                "}").lines();

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectedTypeFile, aComponentFile, bComponentFile);
    assertThat(compilation).succeeded();

    assertThat(compilation).generatedSourceFile("test.DaggerBComponent")
        .containsLines(List.of(generatedComponent));
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void resolutionOrder(CompilerMode compilerMode) {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A(B b) {}",
        "}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class B {",
        "  @Inject B(C c) {}",
        "}");
    JavaFileObject cFile = JavaFileObjects.forSourceLines("test.C",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class C {",
        "  @Inject C() {}",
        "}");
    JavaFileObject xFile = JavaFileObjects.forSourceLines("test.X",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class X {",
        "  @Inject X(C c) {}",
        "}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  A a();",
        "  C c();",
        "  X x();",
        "}");

    String[] generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;",
                "")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines("final class DaggerTestComponent implements TestComponent {",
                "  private B b() {",
                "    return new B(new C());",
                "  }",
                "",
                "  @Override",
                "  public A a() {",
                "    return new A(b());",
                "  }",
                "",
                "  @Override",
                "  public C c() {",
                "    return new C();",
                "  }",
                "",
                "  @Override",
                "  public X x() {",
                "    return new X(new C());",
                "  }",
                "}")
            .lines();

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(aFile, bFile, cFile, xFile, componentFile);
    assertThat(compilation).succeeded();

    assertThat(compilation).generatedSourceFile("test.DaggerTestComponent")
        .containsLines(List.of(generatedComponent));
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void simpleComponent_redundantComponentMethod(CompilerMode compilerMode) {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    JavaFileObject componentSupertypeAFile = JavaFileObjects.forSourceLines("test.SupertypeA",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface SupertypeA {",
        "  SomeInjectableType someInjectableType();",
        "}");
    JavaFileObject componentSupertypeBFile = JavaFileObjects.forSourceLines("test.SupertypeB",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface SupertypeB {",
        "  SomeInjectableType someInjectableType();",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface SimpleComponent extends SupertypeA, SupertypeB {",
        "}");
    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;",
        "");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedImports());
    Collections.addAll(generatedComponent,
        "");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(generatedComponent,
        "final class DaggerSimpleComponent implements SimpleComponent {",
        "  private final DaggerSimpleComponent simpleComponent = this;",
        "",
        "  private DaggerSimpleComponent() {",
        "",
        "",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static SimpleComponent create() {",
        "    return new Builder().build();",
        "  }",
        "",
        "  @Override",
        "  public SomeInjectableType someInjectableType() {",
        "    return new SomeInjectableType();",
        "  }",
        "",
        "  static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public SimpleComponent build() {",
        "      return new DaggerSimpleComponent();",
        "    }",
        "  }",
        "}",
        "");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(
                injectableTypeFile,
                componentSupertypeAFile,
                componentSupertypeBFile,
                componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void simpleComponent_inheritedComponentMethodDep(CompilerMode compilerMode) {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    JavaFileObject componentSupertype = JavaFileObjects.forSourceLines("test.Supertype",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface Supertype {",
        "  SomeInjectableType someInjectableType();",
        "}");
    JavaFileObject depComponentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface SimpleComponent extends Supertype {",
        "}");
    String[] generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerSimpleComponent")
            .addLines(
                "package test;",
                "")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines("final class DaggerSimpleComponent implements SimpleComponent {",
                "  @Override",
                "  public SomeInjectableType someInjectableType() {",
                "    return new SomeInjectableType();",
                "  }",
                "}")
            .lines();
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, componentSupertype, depComponentFile);
    assertThat(compilation).succeeded();

    assertThat(compilation).generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(List.of(generatedComponent));
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void wildcardGenericsRequiresAtProvides(CompilerMode compilerMode) {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A() {}",
        "}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class B<T> {",
        "  @Inject B(T t) {}",
        "}");
    JavaFileObject cFile = JavaFileObjects.forSourceLines("test.C",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class C {",
        "  @Inject C(B<? extends A> bA) {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  C c();",
        "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(aFile, bFile, cFile, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.B<? extends test.A> cannot be provided without a @Provides-annotated method");
  }

  // https://github.com/google/dagger/issues/630
  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void arrayKeyRequiresAtProvides(CompilerMode compilerMode) {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  String[] array();",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("String[] cannot be provided without a @Provides-annotated method");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void componentImplicitlyDependsOnGeneratedType(CompilerMode compilerMode) {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType(GeneratedType generatedType) {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
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
            .withOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.DaggerSimpleComponent");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void componentSupertypeDependsOnGeneratedType(CompilerMode compilerMode) {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface SimpleComponent extends SimpleComponentInterface {}");
    JavaFileObject interfaceFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponentInterface",
            "package test;",
            "",
            "interface SimpleComponentInterface {",
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
            .withOptions(compilerMode.javacopts())
            .compile(componentFile, interfaceFile);
    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.DaggerSimpleComponent");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void scopeAnnotationOnInjectConstructorNotValid(CompilerMode compilerMode) {
    JavaFileObject aScope =
        JavaFileObjects.forSourceLines(
            "test.AScope",
            "package test;",
            "",
            "import jakarta.inject.Scope;",
            "",
            "@Scope",
            "@interface AScope {}");
    JavaFileObject aClass =
        JavaFileObjects.forSourceLines(
            "test.AClass",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class AClass {",
            "  @Inject @AScope AClass() {}",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(aScope, aClass);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Scope annotations are not allowed on @Inject constructors")
        .inFile(aClass)
        .onLine(6);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void unusedSubcomponents_dontResolveExtraBindingsInParentComponents(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");

    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module(subcomponents = Pruned.class)",
            "class TestModule {}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules = TestModule.class)",
            "interface Parent {}");

    JavaFileObject prunedSubcomponent =
        JavaFileObjects.forSourceLines(
            "test.Pruned",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Pruned {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Pruned build();",
            "  }",
            "",
            "  Foo foo();",
            "}");
    List<String> generated = new ArrayList<>();
    Collections.addAll(generated,
        "package test;",
        "");
    Collections.addAll(generated,
        GeneratedLines.generatedImports("import dagger.internal.Preconditions;"));
    Collections.addAll(generated,
        "");
    Collections.addAll(generated,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(generated,
        "final class DaggerParent implements Parent {",
        "  private final DaggerParent parent = this;",
        "",
        "  private DaggerParent() {",
        "",
        "",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static Parent create() {",
        "    return new Builder().build();",
        "  }",
        "",
        "  static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    /**",
        "     * @deprecated This module is declared, but an instance is not used in the component. This method is a no-op. For more, see https://dagger.dev/unused-modules.",
        "     */", "    @Deprecated",
        "    public Builder testModule(TestModule testModule) {",
        "      Preconditions.checkNotNull(testModule);",
        "      return this;",
        "    }",
        "",
        "    public Parent build() {",
        "      return new DaggerParent();",
        "    }",
        "  }",
        "}",
        "");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(foo, module, component, prunedSubcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .containsLines(generated);
  }

  @Test
  void bindsToDuplicateBinding_bindsKeyIsNotDuplicated() {
    JavaFileObject firstModule =
        JavaFileObjects.forSourceLines(
            "test.FirstModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class FirstModule {",
            "  @Provides static String first() { return \"first\"; }",
            "}");
    JavaFileObject secondModule =
        JavaFileObjects.forSourceLines(
            "test.SecondModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class SecondModule {",
            "  @Provides static String second() { return \"second\"; }",
            "}");
    JavaFileObject bindsModule =
        JavaFileObjects.forSourceLines(
            "test.BindsModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class BindsModule {",
            "  @Binds abstract Object bindToDuplicateBinding(String duplicate);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = {FirstModule.class, SecondModule.class, BindsModule.class})",
            "interface TestComponent {",
            "  Object notDuplicated();",
            "}");

    Compilation compilation =
        daggerCompiler().compile(firstModule, secondModule, bindsModule, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("String is bound multiple times")
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void privateMethodUsedOnlyInChildDoesNotUseQualifiedThis(CompilerMode compilerMode) {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules=TestModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import jakarta.inject.Singleton;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides @Singleton static Number number() {",
            "    return 3;",
            "  }",
            "",
            "  @Provides static String string(Number number) {",
            "    return number.toString();",
            "  }",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child {",
            "  String string();",
            "}");

    String[] expectedPattern =
        compilerMode.javaFileBuilder("test.DaggerParent")
            .addLines(
                "package test;")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines(
                "final class DaggerParent implements Parent {",
                "  private String string() {",
                "    return TestModule_StringFactory.string(numberProvider.get());",
                "  }",
                "}")
            .lines();

    Compilation compilation = daggerCompiler().compile(parent, testModule, child);
    assertThat(compilation).succeededWithoutWarnings();

    assertThat(compilation).generatedSourceFile("test.DaggerParent")
        .containsLines(List.of(expectedPattern));
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void componentMethodInChildCallsComponentMethodInParent(CompilerMode compilerMode) {
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "test.Supertype",
            "package test;",
            "",
            "interface Supertype {",
            "  String string();",
            "}");
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules=TestModule.class)",
            "interface Parent extends Supertype {",
            "  Child child();",
            "}");
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import jakarta.inject.Singleton;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides @Singleton static Number number() {",
            "    return 3;",
            "  }",
            "",
            "  @Provides static String string(Number number) {",
            "    return number.toString();",
            "  }",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child extends Supertype {}");

    String[] expectedPattern =
        compilerMode.javaFileBuilder("test.DaggerParent")
            .addLines("package test;")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines(
                "final class DaggerParent implements Parent {",
                "  private static final class ChildImpl implements Child {",
                "    @Override",
                "    public String string() {",
                "      return parent.string();",
                "    }",
                "  }",
                "}")
            .lines();

    Compilation compilation = daggerCompiler().compile(supertype, parent, testModule, child);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation).generatedSourceFile("test.DaggerParent")
        .containsLines(List.of(expectedPattern));
  }

  @Test
  void moduleHasGeneratedQualifier() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String unqualified() {",
            "    return new String();",
            "  }",
            "",
            "  @Provides",
            "  @GeneratedQualifier",
            "  static String qualified() {",
            "    return new String();",
            "  }",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  String unqualified();",
            "}");
    // Generating the qualifier leads to duplicate binding exception.
    JavaFileObject qualifier =
        JavaFileObjects.forSourceLines(
            "test.GeneratedQualifier",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import java.lang.annotation.Retention;",
            "import jakarta.inject.Qualifier;",
            "",
            "@Retention(RUNTIME)",
            "@Qualifier",
            "@interface GeneratedQualifier {}");

    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {",
        "  @Override",
        "  public String unqualified() {",
        // Ensure that the unqualified @Provides method is used.
        "    return TestModule_UnqualifiedFactory.unqualified();",
        "  }",
        "}");

    Compilation compilation =
        daggerCompiler()
            .compile(module, component, qualifier);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void publicComponentType() {
    JavaFileObject publicComponent =
        JavaFileObjects.forSourceLines(
            "test.PublicComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "public interface PublicComponent {}");
    Compilation compilation = daggerCompiler().compile(publicComponent);
    assertThat(compilation).succeeded();
    List<String> daggerPublicComponent = new ArrayList<>();
    Collections.addAll(daggerPublicComponent,
        "package test;",
        "");
    Collections.addAll(daggerPublicComponent,
        GeneratedLines.generatedImports());
    Collections.addAll(daggerPublicComponent,
        "");
    Collections.addAll(daggerPublicComponent,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(daggerPublicComponent,
        "public final class DaggerPublicComponent implements PublicComponent {",
        "  private final DaggerPublicComponent publicComponent = this;",
        "",
        "  private DaggerPublicComponent() {",
        "",
        "",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static PublicComponent create() {",
        "    return new Builder().build();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public PublicComponent build() {",
        "      return new DaggerPublicComponent();",
        "    }",
        "  }",
        "}",
        "");
    assertThat(compilation)
        .generatedSourceFile("test.DaggerPublicComponent")
        .containsLines(
            daggerPublicComponent);
  }

  @Test
  public void componentFactoryInterfaceTest() {
    JavaFileObject parentInterface =
        JavaFileObjects.forSourceLines(
            "test.ParentInterface",
            "package test;",
            "",
            "interface ParentInterface extends ChildInterface.Factory {}");

    JavaFileObject childInterface =
        JavaFileObjects.forSourceLines(
            "test.ChildInterface",
            "package test;",
            "",
            "interface ChildInterface {",
            "  interface Factory {",
            "    ChildInterface child(ChildModule childModule);",
            "  }",
            "}");

    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent extends ParentInterface, Child.Factory {}");

    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child extends ChildInterface {",
            "  interface Factory extends ChildInterface.Factory {",
            "    @Override Child child(ChildModule childModule);",
            "  }",
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
            "class ChildModule {",
            "  @Provides",
            "  int provideInt() {",
            "    return 0;",
            "  }",
            "}");

    Compilation compilation =
        daggerCompiler().compile(parentInterface, childInterface, parent, child, childModule);
    assertThat(compilation).succeeded();
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void providerComponentType(CompilerMode compilerMode) {
    JavaFileObject entryPoint =
        JavaFileObjects.forSourceLines(
            "test.SomeEntryPoint",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Provider;",
            "",
            "public class SomeEntryPoint {",
            "  @Inject SomeEntryPoint(Foo foo, Provider<Foo> fooProvider) {}",
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "public class Foo {",
            "  @Inject Foo(Bar bar) {}",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "public class Bar {",
            "  @Inject Bar() {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "public interface TestComponent {",
            "  SomeEntryPoint someEntryPoint();",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(component, foo, bar, entryPoint);

    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(
            compilerMode
                .javaFileBuilder("test.DaggerSimpleComponent")
                .addLines(
                    "package test;",
                    "")
                .addLines(GeneratedLines.generatedAnnotations())
                .addLines("public final class DaggerTestComponent implements TestComponent {",
                    "  private Provider<Foo> fooProvider;")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  private Foo foo() {",
                    "    return new Foo(new Bar());",
                    "  }",
                    "",
                    "  @Override",
                    "  public SomeEntryPoint someEntryPoint() {",
                    "    return new SomeEntryPoint(foo(), fooProvider);",
                    "  }",
                    "",
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "    this.fooProvider = Foo_Factory.create(Bar_Factory.create());",
                    "  }")
        .addLinesIn(
                    FAST_INIT_MODE,
                    "  @Override",
                    "  public SomeEntryPoint someEntryPoint() {",
                    "    return new SomeEntryPoint(fooProvider.get(), fooProvider);",
                    "  }",
                    "",
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "    this.fooProvider = new SwitchingProvider<>(testComponent, 0);",
                    "  }",
                    "",
                    "  private static final class SwitchingProvider<T> implements Provider<T> {",
                    "    @SuppressWarnings(\"unchecked\")",
                    "    @Override",
                    "    public T get() {",
                    "      switch (id) {",
                    "        case 0: // test.Foo ",
                    "        return (T) new Foo(new Bar());",
                    "        default: throw new AssertionError(id);",
                    "      }",
                    "    }",
                    "  }",
                    "}")
                .lines());
  }

  @Test
  void injectedTypeHasGeneratedParam() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "public final class Foo {",
            "",
            "  @Inject",
            "  public Foo(GeneratedParam param) {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Foo foo();",
            "}");

    Compilation compilation =
        daggerCompiler(
            new GeneratingProcessor(
                "test.GeneratedParam",
                "package test;",
                "",
                "import jakarta.inject.Inject;",
                "",
                "public final class GeneratedParam {",
                "",
                "  @Inject",
                "  public GeneratedParam() {}",
                "}"))
            .compile(foo, component);
    assertThat(compilation).succeededWithoutWarnings();
  }
}
