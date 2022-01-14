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
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.COMPONENT_BUILDER;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.COMPONENT_FACTORY;
import static dagger.internal.codegen.binding.ComponentCreatorKind.BUILDER;
import static dagger.internal.codegen.binding.ComponentCreatorKind.FACTORY;
import static dagger.internal.codegen.binding.ComponentKind.COMPONENT;
import static dagger.internal.codegen.binding.ErrorMessages.componentMessagesFor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Tests for properties of component creators shared by both builders and factories. */
class ComponentCreatorTest {

  private static List<ComponentCreatorTestData> dataSource() {
    List<ComponentCreatorTestData> result = new ArrayList<>();
    result.add(new ComponentCreatorTestData(DEFAULT_MODE, COMPONENT_FACTORY));
    result.add(new ComponentCreatorTestData(DEFAULT_MODE, COMPONENT_BUILDER));
    result.add(new ComponentCreatorTestData(FAST_INIT_MODE, COMPONENT_FACTORY));
    result.add(new ComponentCreatorTestData(FAST_INIT_MODE, COMPONENT_BUILDER));
    return result;
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testEmptyCreator(ComponentCreatorTestData data) {
    JavaFileObject injectableTypeFile =
        JavaFileObjects.forSourceLines(
            "test.SomeInjectableType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class SomeInjectableType {",
            "  @Inject SomeInjectableType() {}",
            "}");
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "interface SimpleComponent {",
            "  SomeInjectableType someInjectableType();",
            "",
            "  @Component.Builder",
            "  static interface Builder {",
            "     SimpleComponent build();",
            "  }",
            "}");
    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(generatedComponent,
        "final class DaggerSimpleComponent implements SimpleComponent {",
        "  private static final class Builder implements SimpleComponent.Builder {",
        "    @Override",
        "    public SimpleComponent build() {",
        "      return new DaggerSimpleComponent();",
        "    }",
        "  }",
        "}");
    Compilation compilation = compile(data, injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(data.processLines(generatedComponent));
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCanInstantiateModulesUserCannotSet(ComponentCreatorTestData data) {
    JavaFileObject module =
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
        data.preprocessedJavaFile(
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
            "    TestComponent build();",
            "  }",
            "}");
    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedImportsIndividual());
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {",
        "  private final TestModule testModule;",
        "",
        "  private final DaggerTestComponent testComponent = this;",
        "",
        "  private DaggerTestComponent(TestModule testModuleParam) {",
        "    this.testModule = testModuleParam;",
        "  }",
        "",
        "  public static TestComponent.Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static TestComponent create() {",
        "    return new Builder().build();",
        "  }",
        "",
        "  @Override",
        "  public String string() {",
        "    return TestModule_StringFactory.string(testModule);",
        "  }",
        "",
        "  private static final class Builder implements TestComponent.Builder {",
        "    @Override",
        "    public TestComponent build() {",
        "      return new DaggerTestComponent(new TestModule());",
        "    }",
        "  }",
        "}");
    Compilation compilation = compile(data, module, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(data.processLines(generatedComponent));
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testMoreThanOneCreatorOfSameTypeFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "interface SimpleComponent {",
            "  @Component.Builder",
            "  static interface Builder {",
            "     SimpleComponent build();",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder2 {",
            "     SimpleComponent build();",
            "  }",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                componentMessagesFor(COMPONENT).moreThanOne(),
                data.process("[test.SimpleComponent.Builder, test.SimpleComponent.Builder2]")))
        .inFile(componentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testBothBuilderAndFactoryFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "interface SimpleComponent {",
            "  @Component.Builder",
            "  static interface Builder {",
            "     SimpleComponent build();",
            "  }",
            "",
            "  @Component.Factory",
            "  interface Factory {",
            "     SimpleComponent create();",
            "  }",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                componentMessagesFor(COMPONENT).moreThanOne(),
                "[test.SimpleComponent.Builder, test.SimpleComponent.Factory]"))
        .inFile(componentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testGenericCreatorTypeFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "interface SimpleComponent {",
            "  @Component.Builder",
            "  interface Builder<T> {",
            "     SimpleComponent build();",
            "  }",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(data.messages.generics()).inFile(componentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorNotInComponentFails(ComponentCreatorTestData data) {
    JavaFileObject builder =
        data.preprocessedJavaFile(
            "test.Builder",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component.Builder",
            "interface Builder {}");
    Compilation compilation = compile(data, builder);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(data.messages.mustBeInComponent()).inFile(builder);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorMissingFactoryMethodFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "interface SimpleComponent {",
            "  @Component.Builder",
            "  interface Builder {}",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(data.messages.missingFactoryMethod())
        .inFile(componentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorWithBindsInstanceNoStaticCreateGenerated(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.javaFileBuilder("test.SimpleComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.BindsInstance;",
                "import dagger.Component;",
                "import jakarta.inject.Provider;",
                "",
                "@Component",
                "interface SimpleComponent {",
                "  Object object();",
                "")
            .addLinesIf(
                BUILDER,
                "  @Component.Builder",
                "  interface Builder {",
                "    @BindsInstance Builder object(Object object);",
                "    SimpleComponent build();",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Component.Factory",
                "  interface Factory {",
                "    SimpleComponent create(@BindsInstance Object object);",
                "  }")
            .addLines("}")
            .build();

    String[] generatedComponent =
        data.javaFileBuilder("test.DaggerSimpleComponent")
            .addLines(
                "package test;")
            .addLines(
                GeneratedLines.generatedImportsIndividual("import dagger.internal.Preconditions;"))
            .addLines(
                GeneratedLines.generatedAnnotationsIndividual())
            .addLines(
                "final class DaggerSimpleComponent implements SimpleComponent {",
                "  private final Object object;",
                "",
                "  private final DaggerSimpleComponent simpleComponent = this;",
                "",
                "  private DaggerSimpleComponent(Object objectParam) {",
                "    this.object = objectParam;",
                "  }",
                "")
            .addLinesIf(
                BUILDER,
                "  public static SimpleComponent.Builder builder() {",
                "    return new Builder();",
                "  }")
            .addLinesIf(
                FACTORY,
                "  public static SimpleComponent.Factory factory() {",
                "    return new Factory();",
                "  }")
            .addLines(
                "",
                "  @Override",
                "  public Object object() {",
                "    return object;",
                "  }",
                "")
            .addLinesIf(
                BUILDER,
                "  private static final class Builder implements SimpleComponent.Builder {",
                "    private Object object;",
                "",
                "    @Override",
                "    public Builder object(Object object) {",
                "      this.object = Preconditions.checkNotNull(object);",
                "      return this;",
                "    }",
                "",
                "    @Override",
                "    public SimpleComponent build() {",
                "      Preconditions.checkBuilderRequirement(object, Object.class);",
                "      return new DaggerSimpleComponent(object);",
                "    }",
                "  }")
            .addLinesIf(
                FACTORY,
                "  private static final class Factory implements SimpleComponent.Factory {",
                "    @Override",
                "    public SimpleComponent create(Object object) {",
                "      Preconditions.checkNotNull(object);",
                "      return new DaggerSimpleComponent(object);",
                "    }",
                "  }")
            .addLines("}")
            .lines();

    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(data.processLines(List.of(generatedComponent)));
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorWithPrimitiveBindsInstance(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.javaFileBuilder("test.SimpleComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.BindsInstance;",
                "import dagger.Component;",
                "import jakarta.inject.Provider;",
                "",
                "@Component",
                "interface SimpleComponent {",
                "  int anInt();",
                "")
            .addLinesIf(
                BUILDER,
                "  @Component.Builder",
                "  interface Builder {",
                "    @BindsInstance Builder i(int i);",
                "    SimpleComponent build();",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Component.Factory",
                "  interface Factory {",
                "    SimpleComponent create(@BindsInstance int i);",
                "  }")
            .addLines(
                "}")
            .build();

    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedImportsIndividual("import dagger.internal.Preconditions;"));
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(generatedComponent,
        "final class DaggerSimpleComponent implements SimpleComponent {",
        "  private final Integer i;",
        "",
        "  private DaggerSimpleComponent(Integer iParam) {",
        "    this.i = iParam;",
        "  }",
        "",
        "  @Override",
        "  public int anInt() {",
        "    return i;",
        "  }",
        "");
    if (data.creatorKind == BUILDER) {
      Collections.addAll(generatedComponent,
          "  private static final class Builder implements SimpleComponent.Builder {",
          "    private Integer i;",
          "",
          "    @Override",
          "    public Builder i(int i) {",
          "      this.i = Preconditions.checkNotNull(i);",
          "      return this;",
          "    }",
          "",
          "    @Override",
          "    public SimpleComponent build() {",
          "      Preconditions.checkBuilderRequirement(i, Integer.class);",
          "      return new DaggerSimpleComponent(i);",
          "    }",
          "  }");
    }
    if (data.creatorKind == FACTORY) {
      Collections.addAll(generatedComponent,
          "  private static final class Factory implements SimpleComponent.Factory {",
          "    @Override",
          "    public SimpleComponent create(int i) {",
          "      Preconditions.checkNotNull(i);",
          "      return new DaggerSimpleComponent(i);",
          "    }",
          "  }");
    }
    Collections.addAll(generatedComponent,
        "}");

    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(generatedComponent);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testPrivateCreatorFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  @Component.Builder",
            "  private interface Builder {}",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(data.messages.isPrivate()).inFile(componentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testNonStaticCreatorFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  @Component.Builder",
            "  abstract class Builder {}",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(data.messages.mustBeStatic()).inFile(componentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testNonAbstractCreatorFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  @Component.Builder",
            "  static class Builder {}",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(data.messages.mustBeAbstract()).inFile(componentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorOneConstructorWithArgsFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  @Component.Builder",
            "  static abstract class Builder {",
            "    Builder(String unused) {}",
            "  }",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(data.messages.invalidConstructor())
        .inFile(componentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorMoreThanOneConstructorFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  @Component.Builder",
            "  static abstract class Builder {",
            "    Builder() {}",
            "    Builder(String unused) {}",
            "  }",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(data.messages.invalidConstructor())
        .inFile(componentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorEnumFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  @Component.Builder",
            "  enum Builder {}",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(data.messages.mustBeClassOrInterface())
        .inFile(componentFile);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorFactoryMethodReturnsWrongTypeFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
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
            "    String build();",
            "  }",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(data.messages.factoryMethodMustReturnComponentType())
        .inFile(componentFile)
        .onLineContaining(data.process("String build();"));
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testCreatorSetterForNonBindsInstancePrimitiveFails(ComponentCreatorTestData data) {
    JavaFileObject component =
        data.javaFileBuilder("test.TestComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Component;",
                "",
                "@Component",
                "interface TestComponent {",
                "  Object object();",
                "")
            .addLinesIf(
                BUILDER,
                "  @Component.Builder",
                "  interface Builder {",
                "    Builder primitive(long l);",
                "    TestComponent build();",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Component.Factory",
                "  interface Factory {",
                "    TestComponent create(long l);",
                "  }")
            .addLines( //
                "}")
            .build();
    Compilation compilation = compile(data, component);
    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContaining(data.messages.nonBindsInstanceParametersMayNotBePrimitives())
        .inFile(component)
        .onLineContaining("(long l)");
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testInheritedBuilderBuildReturnsWrongTypeFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  interface Parent {",
            "    String build();",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                data.messages.inheritedFactoryMethodMustReturnComponentType(), data.process("build")))
        .inFile(componentFile)
        .onLineContaining(data.process("interface Builder"));
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testTwoFactoryMethodsFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
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
            "    SimpleComponent newSimpleComponent();",
            "  }",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(String.format(data.messages.twoFactoryMethods(), data.process("build")))
        .inFile(componentFile)
        .onLineContaining("SimpleComponent newSimpleComponent();");
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testInheritedTwoFactoryMethodsFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
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
            "    SimpleComponent newSimpleComponent();",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                data.messages.inheritedTwoFactoryMethods(), data.process("build()"), "newSimpleComponent()"))
        .inFile(componentFile)
        .onLineContaining(data.process("interface Builder"));
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
        data.javaFileBuilder("test.SimpleComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Component;",
                "import jakarta.inject.Provider;",
                "",
                "@Component(modules = TestModule.class)",
                "abstract class SimpleComponent {",
                "  abstract String s();",
                "")
            .addLinesIf(
                BUILDER,
                "  @Component.Builder",
                "  interface Builder {",
                "    SimpleComponent build();",
                "    void set1(TestModule s);",
                "    void set2(TestModule s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Component.Factory",
                "  interface Factory {",
                "    SimpleComponent create(TestModule m1, TestModule m2);",
                "  }")
            .addLines( //
                "}")
            .build();
    Compilation compilation = compile(data, moduleFile, componentFile);
    assertThat(compilation).failed();
    String elements =
        data.creatorKind.equals(BUILDER)
            ? "[void test.SimpleComponent.Builder.set1(test.TestModule), "
            + "void test.SimpleComponent.Builder.set2(test.TestModule)]"
            : "[test.TestModule m1, test.TestModule m2]";
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                data.messages.multipleSettersForModuleOrDependencyType(), "test.TestModule", elements))
        .inFile(componentFile)
        .onLineContaining(data.process("interface Builder"));
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
        data.javaFileBuilder("test.SimpleComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Component;",
                "import jakarta.inject.Provider;",
                "",
                "@Component(modules = TestModule.class)",
                "abstract class SimpleComponent {",
                "  abstract String s();",
                "")
            .addLinesIf(
                BUILDER,
                "  interface Parent<T> {",
                "    void set1(T t);",
                "  }",
                "",
                "  @Component.Builder",
                "  interface Builder extends Parent<TestModule> {",
                "    SimpleComponent build();",
                "    void set2(TestModule s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  interface Parent<C, T> {",
                "    C create(TestModule m1, T t);",
                "  }",
                "",
                "  @Component.Factory",
                "  interface Factory extends Parent<SimpleComponent, TestModule> {}")
            .addLines( //
                "}")
            .build();
    Compilation compilation = compile(data, moduleFile, componentFile);
    assertThat(compilation).failed();
    String elements =
        data.creatorKind.equals(BUILDER)
            ? "[void test.SimpleComponent.Builder.set1(test.TestModule), "
            + "void test.SimpleComponent.Builder.set2(test.TestModule)]"
            : "[test.TestModule m1, test.TestModule t]";
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                data.messages.multipleSettersForModuleOrDependencyType(), "test.TestModule", elements))
        .inFile(componentFile)
        .onLineContaining(data.process("interface Builder"));
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testExtraSettersFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.javaFileBuilder("test.SimpleComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Component;",
                "import jakarta.inject.Provider;",
                "",
                "@Component(modules = AbstractModule.class)",
                "abstract class SimpleComponent {")
            .addLinesIf(
                BUILDER,
                "  @Component.Builder",
                "  interface Builder {",
                "    SimpleComponent build();",
                "    void abstractModule(AbstractModule abstractModule);",
                "    void other(String s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Component.Factory",
                "  interface Factory {",
                "    SimpleComponent create(AbstractModule abstractModule, String s);",
                "  }")
            .addLines("}")
            .build();
    JavaFileObject abstractModule =
        JavaFileObjects.forSourceLines(
            "test.AbstractModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class AbstractModule {}");
    Compilation compilation = compile(data, componentFile, abstractModule);
    assertThat(compilation).failed();
    String elements =
        data.creatorKind.equals(BUILDER)
            ? "[void test.SimpleComponent.Builder.abstractModule(test.AbstractModule), "
            + "void test.SimpleComponent.Builder.other(String)]"
            : "[test.AbstractModule abstractModule, String s]";
    assertThat(compilation)
        .hadErrorContaining(String.format(data.messages.extraSetters(), elements))
        .inFile(componentFile)
        .onLineContaining(data.process("interface Builder"));
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testMissingSettersFail(ComponentCreatorTestData data) {
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
            "  TestModule(String unused) {}",
            "  @Provides String s() { return null; }",
            "}");
    JavaFileObject module2File =
        JavaFileObjects.forSourceLines(
            "test.Test2Module",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class Test2Module {",
            "  @Provides Integer i() { return null; }",
            "}");
    JavaFileObject module3File =
        JavaFileObjects.forSourceLines(
            "test.Test3Module",
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
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = {TestModule.class, Test2Module.class, Test3Module.class},",
            "           dependencies = OtherComponent.class)",
            "interface TestComponent {",
            "  String string();",
            "  Integer integer();",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    TestComponent create();",
            "  }",
            "}");
    JavaFileObject otherComponent =
        JavaFileObjects.forSourceLines(
            "test.OtherComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface OtherComponent {}");
    Compilation compilation =
        daggerCompiler()
            .compile(moduleFile, module2File, module3File, componentFile, otherComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            // Ignores Test2Module because we can construct it ourselves.
            // TODO(sameb): Ignore Test3Module because it's not used within transitive dependencies.
            String.format(
                data.messages.missingSetters(),
                "[test.TestModule, test.Test3Module, test.OtherComponent]"))
        .inFile(componentFile)
        .onLineContaining(data.process("interface Builder"));
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

    JavaFileObject component =
        data.preprocessedJavaFile(
            "test.HasSupertype",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface HasSupertype extends Supertype {",
            "  @Component.Builder",
            "  interface Builder {",
            "    Supertype build();",
            "  }",
            "}");

    Compilation compilation = compile(data, foo, supertype, component);
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

    JavaFileObject component =
        data.preprocessedJavaFile(
            "test.HasSupertype",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface HasSupertype extends Supertype {",
            "  Bar bar();",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    Supertype build();",
            "  }",
            "}");

    Compilation compilation = compile(data, foo, bar, supertype, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining(
            data.process(
                "test.HasSupertype.Builder.build() returns test.Supertype, but test.HasSupertype "
                    + "declares additional component method(s): bar(). In order to provide "
                    + "type-safe access to these methods, override build() to return "
                    + "test.HasSupertype"))
        .inFile(component)
        .onLine(11);
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void covariantFactoryMethodReturnType_hasNewMethod_factoryMethodInherited(ComponentCreatorTestData data) {
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

    JavaFileObject component =
        data.preprocessedJavaFile(
            "test.HasSupertype",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface HasSupertype extends Supertype {",
            "  Bar bar();",
            "",
            "  @Component.Builder",
            "  interface Builder extends CreatorSupertype {}",
            "}");

    Compilation compilation = compile(data, foo, bar, supertype, creatorSupertype, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining(
            data.process(
                "test.HasSupertype.Builder.build() returns test.Supertype, but test.HasSupertype "
                    + "declares additional component method(s): bar(). In order to provide "
                    + "type-safe access to these methods, override build() to return "
                    + "test.HasSupertype"));
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testGenericsOnFactoryMethodFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
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
            "    <T> SimpleComponent build();",
            "  }",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(data.messages.methodsMayNotHaveTypeParameters())
        .inFile(componentFile)
        .onLineContaining(data.process("<T> SimpleComponent build();"));
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testGenericsOnInheritedFactoryMethodFails(ComponentCreatorTestData data) {
    JavaFileObject componentFile =
        data.preprocessedJavaFile(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  interface Parent {",
            "    <T> SimpleComponent build();",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");
    Compilation compilation = compile(data, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                data.messages.inheritedMethodsMayNotHaveTypeParameters(), data.process("<T>build()")))
        .inFile(componentFile)
        .onLineContaining(data.process("interface Builder"));
  }

  /** Compiles the given files with the set compiler mode's javacopts. */
  Compilation compile(ComponentCreatorTestData data, JavaFileObject... files) {
    List<String> options = data.compilerMode.javacopts();
    return compilerWithOptions(options).compile(files);
  }
}
