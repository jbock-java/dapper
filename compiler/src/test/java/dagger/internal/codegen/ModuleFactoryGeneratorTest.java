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

import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatMethodInUnannotatedClass;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatModuleMethod;
import static io.jbock.common.truth.Truth.assertAbout;
import static io.jbock.testing.compile.CompilationSubject.assertThat;
import static io.jbock.testing.compile.JavaSourceSubjectFactory.javaSource;
import static io.jbock.testing.compile.JavaSourcesSubjectFactory.javaSources;

import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class ModuleFactoryGeneratorTest {

  private static final JavaFileObject NULLABLE =
      JavaFileObjects.forSourceLines(
          "test.Nullable", "package test;", "public @interface Nullable {}");

  // TODO(gak): add tests for invalid combinations of scope and qualifier annotations like we have
  // for @Inject

  @Test
  void providesMethodNotInModule() {
    assertThatMethodInUnannotatedClass("@Provides String provideString() { return null; }")
        .hasError("@Provides methods can only be present within a @Module or @ProducerModule");
  }

  @Test
  void providesMethodAbstract() {
    assertThatModuleMethod("@Provides abstract String abstractMethod();")
        .hasError("@Provides methods cannot be abstract");
  }

  @Test
  void providesMethodPrivate() {
    assertThatModuleMethod("@Provides private String privateMethod() { return null; }")
        .hasError("@Provides methods cannot be private");
  }

  @Test
  void providesMethodReturnVoid() {
    assertThatModuleMethod("@Provides void voidMethod() {}")
        .hasError("@Provides methods must return a value (not void)");
  }

  @Test
  void providesMethodReturnsProvider() {
    assertThatModuleMethod("@Provides Provider<String> provideProvider() {}")
        .hasError("@Provides methods must not return framework types");
  }

  @Test
  void providesMethodReturnsLazy() {
    assertThatModuleMethod("@Provides Lazy<String> provideLazy() {}")
        .hasError("@Provides methods must not return framework types");
  }

  @Test
  void providesMethodWithTypeParameter() {
    assertThatModuleMethod("@Provides <T> String typeParameter() { return null; }")
        .hasError("@Provides methods may not have type parameters");
  }

  @Test
  void modulesWithTypeParamsMustBeAbstract() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "final class TestModule<A> {}");
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Modules with type parameters must be abstract")
        .inFile(moduleFile)
        .onLine(6);
  }

  @Test
  void provideOverriddenByNoProvide() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class Parent {",
            "  @Provides String foo() { return null; }",
            "}");
    assertThatModuleMethod("String foo() { return null; }")
        .withDeclaration("@Module class %s extends Parent { %s }")
        .withAdditionalSources(parent)
        .hasError(
            "Binding methods may not be overridden in modules. Overrides: "
                + "@Provides String test.Parent.foo()");
  }

  @Test
  void provideOverriddenByProvide() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class Parent {",
            "  @Provides String foo() { return null; }",
            "}");
    assertThatModuleMethod("@Provides String foo() { return null; }")
        .withDeclaration("@Module class %s extends Parent { %s }")
        .withAdditionalSources(parent)
        .hasError(
            "Binding methods may not override another method. Overrides: "
                + "@Provides String test.Parent.foo()");
  }

  @Test
  void providesOverridesNonProvides() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "class Parent {",
            "  String foo() { return null; }",
            "}");
    assertThatModuleMethod("@Provides String foo() { return null; }")
        .withDeclaration("@Module class %s extends Parent { %s }")
        .withAdditionalSources(parent)
        .hasError(
            "Binding methods may not override another method. Overrides: "
                + "String test.Parent.foo()");
  }

  @Test
  void validatesIncludedModules() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module(",
            "    includes = {",
            "        Void.class,",
            "        String.class,",
            "    }",
            ")",
            "class TestModule {}");

    Compilation compilation = daggerCompiler().compile(module);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "java.lang.Void is listed as a module, but is not annotated with @Module");
  }

  @Test
  void singleProvidesMethodNoArgs() {
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
            "  @Provides String provideString() {",
            "    return \"\";",
            "  }",
            "}");
    List<String> factoryFile = new ArrayList<>();
    Collections.addAll(factoryFile, "package test;");
    Collections.addAll(
        factoryFile,
        GeneratedLines.generatedImports(
            "import dagger.internal.Factory;", "import dagger.internal.Preconditions;"));
    Collections.addAll(factoryFile, GeneratedLines.generatedAnnotations());
    Collections.addAll(
        factoryFile,
        "public final class TestModule_ProvideStringFactory implements Factory<String> {",
        "  private final TestModule module;",
        "",
        "  public TestModule_ProvideStringFactory(TestModule module) {",
        "    this.module = module;",
        "  }",
        "",
        "  @Override",
        "  public String get() {",
        "    return provideString(module);",
        "  }",
        "",
        "  public static TestModule_ProvideStringFactory create(TestModule module) {",
        "    return new TestModule_ProvideStringFactory(module);",
        "  }",
        "",
        "  public static String provideString(TestModule instance) {",
        "    return Preconditions.checkNotNullFromProvides(instance.provideString());",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.TestModule_ProvideStringFactory", factoryFile);
  }

  @Test
  void singleProvidesMethodNoArgs_disableNullable() {
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
            "  @Provides String provideString() {",
            "    return \"\";",
            "  }",
            "}");
    List<String> factoryFile = new ArrayList<>();
    Collections.addAll(factoryFile, "package test;");
    Collections.addAll(
        factoryFile, GeneratedLines.generatedImports("import dagger.internal.Factory;"));
    Collections.addAll(factoryFile, GeneratedLines.generatedAnnotations());
    Collections.addAll(
        factoryFile,
        "public final class TestModule_ProvideStringFactory implements Factory<String> {",
        "  private final TestModule module;",
        "",
        "  public TestModule_ProvideStringFactory(TestModule module) {",
        "    this.module = module;",
        "  }",
        "",
        "  @Override",
        "  public String get() {",
        "    return provideString(module);",
        "  }",
        "",
        "  public static TestModule_ProvideStringFactory create(TestModule module) {",
        "    return new TestModule_ProvideStringFactory(module);",
        "  }",
        "",
        "  public static String provideString(TestModule instance) {",
        "    return instance.provideString();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .withCompilerOptions("-Adagger.nullableValidation=WARNING")
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.TestModule_ProvideStringFactory", factoryFile);
  }

  @Test
  void nullableProvides() {
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
            "  @Provides @Nullable String provideString() { return null; }",
            "}");
    JavaFileObject factoryFile =
        JavaFileObjectos.forSourceLines( //
                "TestModule_ProvideStringFactory", //
                "package test;") //
            .addLines(
                GeneratedLines.generatedImports( //
                    "import dagger.internal.Factory;", //
                    "import dagger.internal.QualifierMetadata;", //
                    "import dagger.internal.ScopeMetadata;"))
            .addLines( //
                "@ScopeMetadata", //
                "@QualifierMetadata")
            .addLines(GeneratedLines.generatedAnnotations())
            .addLines(
                "public final class TestModule_ProvideStringFactory implements Factory<String> {",
                "  private final TestModule module;",
                "",
                "  public TestModule_ProvideStringFactory(TestModule module) {",
                "    this.module = module;",
                "  }",
                "",
                "  @Override",
                "  @Nullable",
                "  public String get() {",
                "    return provideString(module);",
                "  }",
                "",
                "  public static TestModule_ProvideStringFactory create(TestModule module) {",
                "    return new TestModule_ProvideStringFactory(module);",
                "  }",
                "",
                "  @Nullable",
                "  public static String provideString(TestModule instance) {",
                "    return instance.provideString();",
                "  }",
                "}")
            .build();
    Compilation compilation = daggerCompiler().compile(moduleFile, NULLABLE);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.TestModule_ProvideStringFactory")
        .containsLines(factoryFile);
  }

  @Test
  void multipleProvidesMethods() {
    JavaFileObject classXFile =
        JavaFileObjects.forSourceLines(
            "test.X",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class X {",
            "  @Inject X(String s) {}",
            "}");
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Arrays;",
            "import java.util.List;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides List<Object> provideObjects(",
            "      @QualifierA Object a, @QualifierB Object b) {",
            "    return Arrays.asList(a, b);",
            "  }",
            "",
            "  @Provides @QualifierA Object provideAObject() {",
            "    return new Object();",
            "  }",
            "",
            "  @Provides @QualifierB Object provideBObject() {",
            "    return new Object();",
            "  }",
            "}");
    List<String> listFactoryFile = new ArrayList<>();
    Collections.addAll(listFactoryFile, "package test;");
    Collections.addAll(
        listFactoryFile,
        GeneratedLines.generatedImports(
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            "import java.util.List;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(listFactoryFile, GeneratedLines.generatedAnnotations());
    Collections.addAll(
        listFactoryFile,
        "public final class TestModule_ProvideObjectsFactory implements Factory<List<Object>> {",
        "  private final TestModule module;",
        "  private final Provider<Object> aProvider;",
        "  private final Provider<Object> bProvider;",
        "",
        "  public TestModule_ProvideObjectsFactory(TestModule module, Provider<Object> aProvider,",
        "      Provider<Object> bProvider) {",
        "    this.module = module;",
        "    this.aProvider = aProvider;",
        "    this.bProvider = bProvider;",
        "  }",
        "",
        "  @Override",
        "  public List<Object> get() {",
        "    return provideObjects(module, aProvider.get(), bProvider.get());",
        "  }",
        "",
        "  public static TestModule_ProvideObjectsFactory create(TestModule module,",
        "      Provider<Object> aProvider, Provider<Object> bProvider) {",
        "    return new TestModule_ProvideObjectsFactory(module, aProvider, bProvider);",
        "  }",
        "",
        "  public static List<Object> provideObjects(TestModule instance, Object a, Object b) {",
        "    return Preconditions.checkNotNullFromProvides(instance.provideObjects(a, b));",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(List.of(classXFile, moduleFile, QUALIFIER_A, QUALIFIER_B))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.TestModule_ProvideObjectsFactory", listFactoryFile);
  }

  @Test
  void multipleProvidesMethodsWithSameName() {
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
            "  @Provides Object provide(int i) {",
            "    return i;",
            "  }",
            "",
            "  @Provides String provide() {",
            "    return \"\";",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Cannot have more than one binding method with the same name in a single module")
        .inFile(moduleFile)
        .onLine(8);
    assertThat(compilation)
        .hadErrorContaining(
            "Cannot have more than one binding method with the same name in a single module")
        .inFile(moduleFile)
        .onLine(12);
  }

  @Test
  void providesMethodThrowsChecked() {
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
            "  @Provides int i() throws Exception {",
            "    return 0;",
            "  }",
            "",
            "  @Provides String s() throws Throwable {",
            "    return \"\";",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Provides methods may only throw unchecked exceptions")
        .inFile(moduleFile)
        .onLine(8);
    assertThat(compilation)
        .hadErrorContaining("@Provides methods may only throw unchecked exceptions")
        .inFile(moduleFile)
        .onLine(12);
  }

  @Test
  void providedTypes() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.io.Closeable;",
            "import java.util.Set;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides String string() {",
            "    return null;",
            "  }",
            "",
            "  @Provides Set<String> strings() {",
            "    return null;",
            "  }",
            "",
            "  @Provides Set<? extends Closeable> closeables() {",
            "    return null;",
            "  }",
            "",
            "  @Provides String[] stringArray() {",
            "    return null;",
            "  }",
            "",
            "  @Provides int integer() {",
            "    return 0;",
            "  }",
            "",
            "  @Provides int[] integers() {",
            "    return null;",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).succeeded();
  }

  @Test
  void privateModule() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.Enclosing",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "final class Enclosing {",
            "  @Module private static final class PrivateModule {",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Modules cannot be private")
        .inFile(moduleFile)
        .onLine(6);
  }

  @Test
  void enclosedInPrivateModule() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.Enclosing",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "final class Enclosing {",
            "  private static final class PrivateEnclosing {",
            "    @Module static final class TestModule {",
            "    }",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Modules cannot be enclosed in private types")
        .inFile(moduleFile)
        .onLine(7);
  }

  @Test
  void publicModuleNonPublicIncludes() {
    JavaFileObject publicModuleFile =
        JavaFileObjects.forSourceLines(
            "test.PublicModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module(includes = {",
            "    BadNonPublicModule.class, OtherPublicModule.class, OkNonPublicModule.class",
            "})",
            "public final class PublicModule {",
            "}");
    JavaFileObject badNonPublicModuleFile =
        JavaFileObjects.forSourceLines(
            "test.BadNonPublicModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class BadNonPublicModule {",
            "  @Provides",
            "  int provideInt() {",
            "    return 42;",
            "  }",
            "}");
    JavaFileObject okNonPublicModuleFile =
        JavaFileObjects.forSourceLines(
            "test.OkNonPublicModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class OkNonPublicModule {",
            "  @Provides",
            "  static String provideString() {",
            "    return \"foo\";",
            "  }",
            "}");
    JavaFileObject otherPublicModuleFile =
        JavaFileObjects.forSourceLines(
            "test.OtherPublicModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "public final class OtherPublicModule {",
            "}");
    Compilation compilation =
        daggerCompiler()
            .compile(
                publicModuleFile,
                badNonPublicModuleFile,
                okNonPublicModuleFile,
                otherPublicModuleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "This module is public, but it includes non-public (or effectively non-public) modules "
                + "(test.BadNonPublicModule) that have non-static, non-abstract binding methods. "
                + "Either reduce the visibility of this module, make the included modules public, "
                + "or make all of the binding methods on the included modules abstract or static.")
        .inFile(publicModuleFile)
        .onLine(8);
  }

  @Test
  void parameterizedModuleWithStaticProvidesMethodOfGenericType() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.ParameterizedModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.List;",
            "import java.util.ArrayList;",
            "import java.util.Map;",
            "import java.util.HashMap;",
            "",
            "@Module abstract class ParameterizedModule<T> {",
            "  @Provides List<T> provideListT() {",
            "    return new ArrayList<>();",
            "  }",
            "",
            "  @Provides static Map<String, Number> provideMapStringNumber() {",
            "    return new HashMap<>();",
            "  }",
            "",
            "  @Provides static Object provideNonGenericType() {",
            "    return new Object();",
            "  }",
            "",
            "  @Provides static String provideNonGenericTypeWithDeps(Object o) {",
            "    return o.toString();",
            "  }",
            "}");

    List<String> provideMapStringNumberFactory = new ArrayList<>();
    Collections.addAll(provideMapStringNumberFactory, "package test;");
    Collections.addAll(
        provideMapStringNumberFactory,
        GeneratedLines.generatedImports(
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            "import java.util.Map;"));
    Collections.addAll(provideMapStringNumberFactory, GeneratedLines.generatedAnnotations());
    Collections.addAll(
        provideMapStringNumberFactory,
        "public final class ParameterizedModule_ProvideMapStringNumberFactory implements Factory<Map<String, Number>> {",
        "  @Override",
        "  public Map<String, Number> get() {",
        "    return provideMapStringNumber();",
        "  }",
        "",
        "  public static ParameterizedModule_ProvideMapStringNumberFactory create() {",
        "    return InstanceHolder.INSTANCE;",
        "  }",
        "",
        "  public static Map<String, Number> provideMapStringNumber() {",
        "    return Preconditions.checkNotNullFromProvides(ParameterizedModule.provideMapStringNumber());",
        "  }",
        "",
        "  private static final class InstanceHolder {",
        "    private static final ParameterizedModule_ProvideMapStringNumberFactory INSTANCE = new ParameterizedModule_ProvideMapStringNumberFactory();",
        "  }",
        "}");

    List<String> provideNonGenericTypeFactory = new ArrayList<>();
    Collections.addAll(
        provideNonGenericTypeFactory, //
        "package test;");
    Collections.addAll(
        provideNonGenericTypeFactory, //
        GeneratedLines.generatedImports( //
            "import dagger.internal.Factory;", //
            "import dagger.internal.Preconditions;"));
    Collections.addAll(
        provideNonGenericTypeFactory, //
        GeneratedLines.generatedAnnotations());
    Collections.addAll(
        provideNonGenericTypeFactory,
        "public final class ParameterizedModule_ProvideNonGenericTypeFactory implements Factory<Object> {",
        "  @Override",
        "  public Object get() {",
        "    return provideNonGenericType();",
        "  }",
        "",
        "  public static ParameterizedModule_ProvideNonGenericTypeFactory create() {",
        "    return InstanceHolder.INSTANCE;",
        "  }",
        "",
        "  public static Object provideNonGenericType() {",
        "    return Preconditions.checkNotNullFromProvides(ParameterizedModule.provideNonGenericType());",
        "  }",
        "",
        "  private static final class InstanceHolder {",
        "    private static final ParameterizedModule_ProvideNonGenericTypeFactory INSTANCE = new ParameterizedModule_ProvideNonGenericTypeFactory();",
        "  }",
        "}");

    List<String> provideNonGenericTypeWithDepsFactory = new ArrayList<>();
    Collections.addAll( //
        provideNonGenericTypeWithDepsFactory, //
        "package test;");
    Collections.addAll(
        provideNonGenericTypeWithDepsFactory,
        GeneratedLines.generatedImports(
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(provideNonGenericTypeWithDepsFactory, GeneratedLines.generatedAnnotations());
    Collections.addAll(
        provideNonGenericTypeWithDepsFactory,
        "public final class ParameterizedModule_ProvideNonGenericTypeWithDepsFactory implements Factory<String> {",
        "  private final Provider<Object> oProvider;",
        "",
        "  public ParameterizedModule_ProvideNonGenericTypeWithDepsFactory(Provider<Object> oProvider) {",
        "    this.oProvider = oProvider;",
        "  }",
        "",
        "  @Override",
        "  public String get() {",
        "    return provideNonGenericTypeWithDeps(oProvider.get());",
        "  }",
        "",
        "  public static ParameterizedModule_ProvideNonGenericTypeWithDepsFactory create(",
        "      Provider<Object> oProvider) {",
        "    return new ParameterizedModule_ProvideNonGenericTypeWithDepsFactory(oProvider);",
        "  }",
        "",
        "  public static String provideNonGenericTypeWithDeps(Object o) {",
        "    return Preconditions.checkNotNullFromProvides(ParameterizedModule.provideNonGenericTypeWithDeps(o));",
        "  }",
        "}");

    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines(
            "test.ParameterizedModule_ProvideMapStringNumberFactory", provideMapStringNumberFactory)
        .and()
        .containsLines(
            "test.ParameterizedModule_ProvideNonGenericTypeFactory", provideNonGenericTypeFactory)
        .and()
        .containsLines(
            "test.ParameterizedModule_ProvideNonGenericTypeWithDepsFactory",
            provideNonGenericTypeWithDepsFactory);
  }

  private static final JavaFileObject QUALIFIER_A =
      JavaFileObjects.forSourceLines(
          "test.QualifierA",
          "package test;",
          "",
          "import jakarta.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierA {}");

  private static final JavaFileObject QUALIFIER_B =
      JavaFileObjects.forSourceLines(
          "test.QualifierB",
          "package test;",
          "",
          "import jakarta.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierB {}");

  @Test
  void providesMethodMultipleQualifiersOnMethod() {
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
            "  @Provides @QualifierA @QualifierB String provideString() {",
            "    return \"foo\";",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(moduleFile, QUALIFIER_A, QUALIFIER_B);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("may not use more than one @Qualifier");
  }

  @Test
  void providesMethodMultipleQualifiersOnParameter() {
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
            "  @Provides static String provideString(@QualifierA @QualifierB Object object) {",
            "    return \"foo\";",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(moduleFile, QUALIFIER_A, QUALIFIER_B);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("may not use more than one @Qualifier");
  }

  @Test
  void providesMethodWildcardDependency() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import jakarta.inject.Provider;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides static String provideString(Provider<? extends Number> numberProvider) {",
            "    return \"foo\";",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(moduleFile, QUALIFIER_A, QUALIFIER_B);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Dagger does not support injecting Provider<T>, Lazy<T>, Producer<T>, or Produced<T> "
                + "when T is a wildcard type such as ? extends java.lang.Number");
  }

  private static final JavaFileObject SCOPE_A =
      JavaFileObjects.forSourceLines(
          "test.ScopeA",
          "package test;",
          "",
          "import jakarta.inject.Scope;",
          "",
          "@Scope @interface ScopeA {}");

  private static final JavaFileObject SCOPE_B =
      JavaFileObjects.forSourceLines(
          "test.ScopeB",
          "package test;",
          "",
          "import jakarta.inject.Scope;",
          "",
          "@Scope @interface ScopeB {}");

  @Test
  void providesMethodMultipleScopes() {
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
            "  @Provides",
            "  @ScopeA",
            "  @ScopeB",
            "  String provideString() {",
            "    return \"foo\";",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(moduleFile, SCOPE_A, SCOPE_B);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("cannot use more than one @Scope")
        .inFile(moduleFile)
        .onLineContaining("@ScopeA");
    assertThat(compilation)
        .hadErrorContaining("cannot use more than one @Scope")
        .inFile(moduleFile)
        .onLineContaining("@ScopeB");
  }

  @Test
  void proxyMethodsConflictWithOtherFactoryMethods() {
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
            "  static int get() { return 1; }",
            "",
            "  @Provides",
            "  static boolean create() { return true; }",
            "}");

    Compilation compilation = daggerCompiler().compile(module);
    assertThat(compilation).succeededWithoutWarnings();
    List<String> generatedGetFactory = new ArrayList<>();
    Collections.addAll(generatedGetFactory, "package test;");
    Collections.addAll(generatedGetFactory, GeneratedLines.generatedAnnotations());
    Collections.addAll(
        generatedGetFactory,
        "public final class TestModule_GetFactory implements Factory<Integer> {",
        "  @Override",
        "  public Integer get() {",
        "    return proxyGet();",
        "  }",
        "",
        "  public static TestModule_GetFactory create() {",
        "    return InstanceHolder.INSTANCE;",
        "  }",
        "",
        "  public static int proxyGet() {",
        "    return TestModule.get();",
        "  }",
        "}");
    assertThat(compilation)
        .generatedSourceFile("test.TestModule_GetFactory")
        .containsLines(generatedGetFactory);

    List<String> generatedCreateFactory = new ArrayList<>();
    Collections.addAll(generatedCreateFactory, "package test;");
    Collections.addAll(generatedCreateFactory, GeneratedLines.generatedAnnotations());
    Collections.addAll(
        generatedCreateFactory,
        "public final class TestModule_CreateFactory implements Factory<Boolean> {",
        "  @Override",
        "  public Boolean get() {",
        "    return proxyCreate();",
        "  }",
        "",
        "  public static TestModule_CreateFactory create() {",
        "    return InstanceHolder.INSTANCE;",
        "  }",
        "",
        "  public static boolean proxyCreate() {",
        "    return TestModule.create();",
        "  }",
        "}");
    assertThat(compilation)
        .generatedSourceFile("test.TestModule_CreateFactory")
        .containsLines(generatedCreateFactory);
  }

  private static final String BINDS_METHOD = "@Binds abstract Foo bindFoo(FooImpl impl);";
  private static final String STATIC_PROVIDES_METHOD =
      "@Provides static Bar provideBar() { return new Bar(); }";
  private static final String INSTANCE_PROVIDES_METHOD =
      "@Provides Baz provideBaz() { return new Baz(); }";
  private static final String SOME_ABSTRACT_METHOD = "abstract void blah();";

  @Test
  void bindsWithInstanceProvides() {
    Compilation compilation = compileMethodCombination(BINDS_METHOD, INSTANCE_PROVIDES_METHOD);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "A @Module may not contain both non-static and abstract binding methods");
  }

  @Test
  void bindsWithStaticProvides() {
    assertThat(compileMethodCombination(BINDS_METHOD, STATIC_PROVIDES_METHOD)).succeeded();
  }

  @Test
  void instanceProvidesWithAbstractMethod() {
    assertThat(compileMethodCombination(INSTANCE_PROVIDES_METHOD, SOME_ABSTRACT_METHOD))
        .succeeded();
  }

  private Compilation compileMethodCombination(String... methodLines) {
    JavaFileObject fooFile =
        JavaFileObjects.forSourceLines("test.Foo", "package test;", "", "interface Foo {}");
    JavaFileObject fooImplFile =
        JavaFileObjects.forSourceLines(
            "test.FooImpl",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class FooImpl implements Foo {",
            "  @Inject FooImpl() {}",
            "}");
    JavaFileObject barFile =
        JavaFileObjects.forSourceLines("test.Bar", "package test;", "", "final class Bar {}");
    JavaFileObject bazFile =
        JavaFileObjects.forSourceLines("test.Baz", "package test;", "", "final class Baz {}");

    List<String> moduleLines =
        new ArrayList<>(
            Arrays.asList(
                "package test;",
                "",
                "import dagger.Binds;",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import java.util.Set;",
                "",
                "@Module abstract class TestModule {"));
    Collections.addAll(moduleLines, methodLines);
    moduleLines.add("}");

    JavaFileObject bindsMethodAndInstanceProvidesMethodModuleFile =
        JavaFileObjects.forSourceLines("test.TestModule", moduleLines);
    return daggerCompiler()
        .compile(
            fooFile, fooImplFile, barFile, bazFile, bindsMethodAndInstanceProvidesMethodModuleFile);
  }
}
