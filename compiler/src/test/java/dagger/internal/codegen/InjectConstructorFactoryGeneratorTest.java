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

import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static io.jbock.common.truth.Truth.assertAbout;
import static io.jbock.testing.compile.CompilationSubject.assertThat;
import static io.jbock.testing.compile.JavaSourceSubjectFactory.javaSource;
import static io.jbock.testing.compile.JavaSourcesSubjectFactory.javaSources;

import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

// TODO(gak): add tests for generation in the default package.
final class InjectConstructorFactoryGeneratorTest {
  private static final JavaFileObject QUALIFIER_A =
      JavaFileObjects.forSourceLines("test.QualifierA",
          "package test;",
          "",
          "import jakarta.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierA {}");
  private static final JavaFileObject QUALIFIER_B =
      JavaFileObjects.forSourceLines("test.QualifierB",
          "package test;",
          "",
          "import jakarta.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierB {}");
  private static final JavaFileObject SCOPE_A =
      JavaFileObjects.forSourceLines("test.ScopeA",
          "package test;",
          "",
          "import jakarta.inject.Scope;",
          "",
          "@Scope @interface ScopeA {}");
  private static final JavaFileObject SCOPE_B =
      JavaFileObjects.forSourceLines("test.ScopeB",
          "package test;",
          "",
          "import jakarta.inject.Scope;",
          "",
          "@Scope @interface ScopeB {}");

  @Test
  void injectOnPrivateConstructor() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.PrivateConstructor",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class PrivateConstructor {",
        "  @Inject private PrivateConstructor() {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private constructors")
        .inFile(file)
        .onLine(6);
  }

  @Test
  void injectConstructorOnInnerClass() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class OuterClass {",
        "  class InnerClass {",
        "    @Inject InnerClass() {}",
        "  }",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@Inject constructors are invalid on inner classes. "
                + "Did you mean to make the class static?")
        .inFile(file)
        .onLine(7);
  }

  @Test
  void injectConstructorOnAbstractClass() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.AbstractClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "abstract class AbstractClass {",
        "  @Inject AbstractClass() {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Inject is nonsense on the constructor of an abstract class")
        .inFile(file)
        .onLine(6);
  }

  @Test
  void injectConstructorOnGenericClass() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class GenericClass<T> {",
        "  @Inject GenericClass(T t) {}",
        "}");
    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImports(
            "import dagger.internal.Factory;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(expected,
        "public final class GenericClass_Factory<T> implements Factory<GenericClass<T>> {",
        "  private final Provider<T> tProvider;",
        "",
        "  public GenericClass_Factory(Provider<T> tProvider) {",
        "    this.tProvider = tProvider;",
        "  }",
        "",
        "  @Override",
        "  public GenericClass<T> get() {",
        "    return newInstance(tProvider.get());",
        "  }",
        "",
        "  public static <T> GenericClass_Factory<T> create(Provider<T> tProvider) {",
        "    return new GenericClass_Factory<T>(tProvider);",
        "  }",
        "",
        "  public static <T> GenericClass<T> newInstance(T t) {",
        "    return new GenericClass<T>(t);",
        "  }",
        "}");
    assertAbout(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.GenericClass_Factory", expected);
  }

  @Test
  void genericClassWithNoDependencies() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class GenericClass<T> {",
        "  @Inject GenericClass() {}",
        "}");
    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImports("import dagger.internal.Factory;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(expected,
        "public final class GenericClass_Factory<T> implements Factory<GenericClass<T>> {",
        "  @Override",
        "  public GenericClass<T> get() {",
        "    return newInstance();",
        "  }",
        "",
        "  public static <T> GenericClass_Factory<T> create() {",
        "    return InstanceHolder.INSTANCE;",
        "  }",
        "",
        "  public static <T> GenericClass<T> newInstance() {",
        "    return new GenericClass<T>();",
        "  }",
        "",
        "  private static final class InstanceHolder {",
        "    @SuppressWarnings(\"rawtypes\")",
        "    private static final GenericClass_Factory INSTANCE = new GenericClass_Factory();",
        "  }",
        "}");
    assertAbout(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.GenericClass_Factory", expected);
  }

  @Test
  void twoGenericTypes() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class GenericClass<A, B> {",
        "  @Inject GenericClass(A a, B b) {}",
        "}");
    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImports(
            "import dagger.internal.Factory;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(expected,
        "public final class GenericClass_Factory<A, B> implements Factory<GenericClass<A, B>> {",
        "  private final Provider<A> aProvider;",
        "  private final Provider<B> bProvider;",
        "",
        "  public GenericClass_Factory(Provider<A> aProvider, Provider<B> bProvider) {",
        "    this.aProvider = aProvider;",
        "    this.bProvider = bProvider;",
        "  }",
        "",
        "  @Override",
        "  public GenericClass<A, B> get() {",
        "    return newInstance(aProvider.get(), bProvider.get());",
        "  }",
        "",
        "  public static <A, B> GenericClass_Factory<A, B> create(Provider<A> aProvider,",
        "      Provider<B> bProvider) {",
        "    return new GenericClass_Factory<A, B>(aProvider, bProvider);",
        "  }",
        "",
        "  public static <A, B> GenericClass<A, B> newInstance(A a, B b) {",
        "    return new GenericClass<A, B>(a, b);",
        "  }",
        "}");
    assertAbout(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.GenericClass_Factory", expected);
  }

  @Test
  void boundedGenerics() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "import java.util.List;",
        "",
        "class GenericClass<A extends Number & Comparable<A>,",
        "    B extends List<? extends String>,",
        "    C extends List<? super String>> {",
        "  @Inject GenericClass(A a, B b, C c) {}",
        "}");
    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImports(
            "import dagger.internal.Factory;",
            "import java.util.List;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(expected,
        "public final class GenericClass_Factory<A extends Number & Comparable<A>, B extends List<? extends String>, C extends List<? super String>> implements Factory<GenericClass<A, B, C>> {",
        "  private final Provider<A> aProvider;",
        "  private final Provider<B> bProvider;",
        "  private final Provider<C> cProvider;",
        "",
        "  public GenericClass_Factory(Provider<A> aProvider, Provider<B> bProvider, Provider<C> cProvider) {",
        "    this.aProvider = aProvider;",
        "    this.bProvider = bProvider;",
        "    this.cProvider = cProvider;",
        "  }",
        "",
        "  @Override",
        "  public GenericClass<A, B, C> get() {",
        "    return newInstance(aProvider.get(), bProvider.get(), cProvider.get());",
        "  }",
        "",
        "  public static <A extends Number & Comparable<A>, B extends List<? extends String>, C extends List<? super String>> GenericClass_Factory<A, B, C> create(",
        "      Provider<A> aProvider, Provider<B> bProvider, Provider<C> cProvider) {",
        "    return new GenericClass_Factory<A, B, C>(aProvider, bProvider, cProvider);",
        "  }",
        "",
        "  public static <A extends Number & Comparable<A>, B extends List<? extends String>, C extends List<? super String>> GenericClass<A, B, C> newInstance(",
        "      A a, B b, C c) {",
        "    return new GenericClass<A, B, C>(a, b, c);",
        "  }",
        "}");
    assertAbout(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.GenericClass_Factory", expected);
  }

  @Test
  void multipleSameTypesWithGenericsAndQualifiersAndLazies() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Provider;",
        "import dagger.Lazy;",
        "",
        "class GenericClass<A, B> {",
        "  @Inject GenericClass(A a, A a2, Provider<A> pa, @QualifierA A qa, Lazy<A> la, ",
        "                       String s, String s2, Provider<String> ps, ",
        "                       @QualifierA String qs, Lazy<String> ls,",
        "                       B b, B b2, Provider<B> pb, @QualifierA B qb, Lazy<B> lb) {}",
        "}");
    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImports(
            "import dagger.Lazy;",
            "import dagger.internal.DoubleCheck;",
            "import dagger.internal.Factory;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(expected,
        "public final class GenericClass_Factory<A, B> implements Factory<GenericClass<A, B>> {",
        "  private final Provider<A> aProvider;",
        "  private final Provider<A> a2Provider;",
        "  private final Provider<A> paProvider;",
        "  private final Provider<A> qaProvider;",
        "  private final Provider<A> laProvider;",
        "  private final Provider<String> sProvider;",
        "  private final Provider<String> s2Provider;",
        "  private final Provider<String> psProvider;",
        "  private final Provider<String> qsProvider;",
        "  private final Provider<String> lsProvider;",
        "  private final Provider<B> bProvider;",
        "  private final Provider<B> b2Provider;",
        "  private final Provider<B> pbProvider;",
        "  private final Provider<B> qbProvider;",
        "  private final Provider<B> lbProvider;",
        "",
        "  public GenericClass_Factory(Provider<A> aProvider, Provider<A> a2Provider, Provider<A> paProvider,",
        "      Provider<A> qaProvider, Provider<A> laProvider, Provider<String> sProvider,",
        "      Provider<String> s2Provider, Provider<String> psProvider, Provider<String> qsProvider,",
        "      Provider<String> lsProvider, Provider<B> bProvider, Provider<B> b2Provider,",
        "      Provider<B> pbProvider, Provider<B> qbProvider, Provider<B> lbProvider) {",
        "    this.aProvider = aProvider;",
        "    this.a2Provider = a2Provider;",
        "    this.paProvider = paProvider;",
        "    this.qaProvider = qaProvider;",
        "    this.laProvider = laProvider;",
        "    this.sProvider = sProvider;",
        "    this.s2Provider = s2Provider;",
        "    this.psProvider = psProvider;",
        "    this.qsProvider = qsProvider;",
        "    this.lsProvider = lsProvider;",
        "    this.bProvider = bProvider;",
        "    this.b2Provider = b2Provider;",
        "    this.pbProvider = pbProvider;",
        "    this.qbProvider = qbProvider;",
        "    this.lbProvider = lbProvider;",
        "  }",
        "",
        "  @Override",
        "  public GenericClass<A, B> get() {",
        "    return newInstance(aProvider.get(), a2Provider.get(), paProvider, qaProvider.get(), DoubleCheck.lazy(laProvider), sProvider.get(), s2Provider.get(), psProvider, qsProvider.get(), DoubleCheck.lazy(lsProvider), bProvider.get(), b2Provider.get(), pbProvider, qbProvider.get(), DoubleCheck.lazy(lbProvider));",
        "  }",
        "",
        "  public static <A, B> GenericClass_Factory<A, B> create(Provider<A> aProvider,",
        "      Provider<A> a2Provider, Provider<A> paProvider, Provider<A> qaProvider,",
        "      Provider<A> laProvider, Provider<String> sProvider, Provider<String> s2Provider,",
        "      Provider<String> psProvider, Provider<String> qsProvider, Provider<String> lsProvider,",
        "      Provider<B> bProvider, Provider<B> b2Provider, Provider<B> pbProvider, Provider<B> qbProvider,",
        "      Provider<B> lbProvider) {",
        "    return new GenericClass_Factory<A, B>(aProvider, a2Provider, paProvider, qaProvider, laProvider, sProvider, s2Provider, psProvider, qsProvider, lsProvider, bProvider, b2Provider, pbProvider, qbProvider, lbProvider);",
        "  }",
        "",
        "  public static <A, B> GenericClass<A, B> newInstance(A a, A a2, Provider<A> pa, A qa, Lazy<A> la,",
        "      String s, String s2, Provider<String> ps, String qs, Lazy<String> ls, B b, B b2,",
        "      Provider<B> pb, B qb, Lazy<B> lb) {",
        "    return new GenericClass<A, B>(a, a2, pa, qa, la, s, s2, ps, qs, ls, b, b2, pb, qb, lb);",
        "  }",
        "}");
    assertAbout(javaSources()).that(List.of(file, QUALIFIER_A))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.GenericClass_Factory", expected);
  }

  @Test
  void multipleInjectConstructors() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.TooManyInjectConstructors",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class TooManyInjectConstructors {",
        "  @Inject TooManyInjectConstructors() {}",
        "  TooManyInjectConstructors(int i) {}",
        "  @Inject TooManyInjectConstructors(String s) {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "Type test.TooManyInjectConstructors may only contain one injected constructor. "
                + "Found: ["
                + "TooManyInjectConstructors(), "
                + "TooManyInjectConstructors(java.lang.String)"
                + "]")
        .inFile(file)
        .onLine(5);
  }

  @Test
  void multipleQualifiersOnInjectConstructorParameter() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MultipleQualifierConstructorParam",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class MultipleQualifierConstructorParam {",
        "  @Inject MultipleQualifierConstructorParam(@QualifierA @QualifierB String s) {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file, QUALIFIER_A, QUALIFIER_B);
    assertThat(compilation).failed();
    // for whatever reason, javac only reports the error once on the constructor
    assertThat(compilation)
        .hadErrorContaining("A single dependency request may not use more than one @Qualifier")
        .inFile(file)
        .onLine(6);
  }

  @Test
  void injectConstructorOnClassWithMultipleScopes() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MultipleScopeClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "@ScopeA @ScopeB class MultipleScopeClass {",
        "  @Inject MultipleScopeClass() {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file, SCOPE_A, SCOPE_B);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("A single binding may not declare more than one @Scope")
        .inFile(file)
        .onLine(5)
        .atColumn(1);
    assertThat(compilation)
        .hadErrorContaining("A single binding may not declare more than one @Scope")
        .inFile(file)
        .onLine(5)
        .atColumn(9);
  }

  @Test
  void injectConstructorWithQualifier() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MultipleScopeClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class MultipleScopeClass {",
        "  @Inject",
        "  @QualifierA",
        "  @QualifierB",
        "  MultipleScopeClass() {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file, QUALIFIER_A, QUALIFIER_B);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Qualifier annotations are not allowed on @Inject constructors")
        .inFile(file)
        .onLine(7);
    assertThat(compilation)
        .hadErrorContaining("@Qualifier annotations are not allowed on @Inject constructors")
        .inFile(file)
        .onLine(8);
  }

  @Test
  void injectConstructorWithCheckedExceptionsError() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.CheckedExceptionClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class CheckedExceptionClass {",
        "  @Inject CheckedExceptionClass() throws Exception {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support checked exceptions on @Inject constructors")
        .inFile(file)
        .onLine(6);
  }

  @Test
  void injectConstructorWithCheckedExceptionsWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.CheckedExceptionClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class CheckedExceptionClass {",
        "  @Inject CheckedExceptionClass() throws Exception {}",
        "}");
    Compilation compilation =
        compilerWithOptions("-Adagger.privateMemberValidation=WARNING").compile(file);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining("Dagger does not support checked exceptions on @Inject constructors")
        .inFile(file)
        .onLine(6);
  }

  @Test
  void privateInjectClassError() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static final class InnerClass {",
        "    @Inject InnerClass() {}",
        "  }",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(7);
  }

  @Test
  void privateInjectClassWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static final class InnerClass {",
        "    @Inject InnerClass() {}",
        "  }",
        "}");
    Compilation compilation =
        compilerWithOptions("-Adagger.privateMemberValidation=WARNING").compile(file);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(7);
  }

  @Test
  void nestedInPrivateInjectClassError() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static final class MiddleClass {",
        "    static final class InnerClass {",
        "      @Inject InnerClass() {}",
        "    }",
        "  }",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(8);
  }

  @Test
  void nestedInPrivateInjectClassWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static final class MiddleClass {",
        "    static final class InnerClass {",
        "      @Inject InnerClass() {}",
        "    }",
        "  }",
        "}");
    Compilation compilation =
        compilerWithOptions("-Adagger.privateMemberValidation=WARNING").compile(file);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(8);
  }

  @Test
  void privateInjectFieldWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.PrivateInjectField",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class PrivateInjectField {",
        "  @Inject private String s;",
        "}");
    Compilation compilation =
        compilerWithOptions("-Adagger.privateMemberValidation=WARNING").compile(file);
    assertThat(compilation).succeeded(); // TODO: Verify warning message when supported
  }

  @Test
  void privateInjectMethodWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.PrivateInjectMethod",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class PrivateInjectMethod {",
        "  @Inject private void method(){}",
        "}");
    Compilation compilation =
        compilerWithOptions("-Adagger.privateMemberValidation=WARNING").compile(file);
    assertThat(compilation).succeeded(); // TODO: Verify warning message when supported
  }

  @Test
  void injectConstructor() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(String s) {}",
        "}");
    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImports(
            "import dagger.internal.Factory;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(expected,
        "public final class InjectConstructor_Factory implements Factory<InjectConstructor> {",
        "  private final Provider<String> sProvider;",
        "",
        "  public InjectConstructor_Factory(Provider<String> sProvider) {",
        "    this.sProvider = sProvider;",
        "  }",
        "",
        "  @Override",
        "  public InjectConstructor get() {",
        "    return newInstance(sProvider.get());",
        "  }",
        "",
        "  public static InjectConstructor_Factory create(Provider<String> sProvider) {",
        "    return new InjectConstructor_Factory(sProvider);",
        "  }",
        "",
        "  public static InjectConstructor newInstance(String s) {",
        "    return new InjectConstructor(s);",
        "  }",
        "}");
    assertAbout(javaSource()).that(file).processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.InjectConstructor_Factory", expected);
  }

  @Test
  void wildcardDependency() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import java.util.List;",
        "import jakarta.inject.Inject;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(List<?> objects) {}",
        "}");
    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImports(
            "import dagger.internal.Factory;",
            "import java.util.List;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(expected,
        "public final class InjectConstructor_Factory implements Factory<InjectConstructor> {",
        "  private final Provider<List<?>> objectsProvider;",
        "",
        "  public InjectConstructor_Factory(Provider<List<?>> objectsProvider) {",
        "    this.objectsProvider = objectsProvider;",
        "  }",
        "",
        "  @Override",
        "  public InjectConstructor get() {",
        "    return newInstance(objectsProvider.get());",
        "  }",
        "",
        "  public static InjectConstructor_Factory create(Provider<List<?>> objectsProvider) {",
        "    return new InjectConstructor_Factory(objectsProvider);",
        "  }",
        "",
        "  public static InjectConstructor newInstance(List<?> objects) {",
        "    return new InjectConstructor(objects);",
        "  }",
        "}");
    assertAbout(javaSource()).that(file).processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.InjectConstructor_Factory", expected);
  }

  @Test
  void basicNameCollision() {
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("other.pkg.Factory",
        "package other.pkg;",
        "",
        "public class Factory {}");
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "import other.pkg.Factory;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(Factory factory) {}",
        "}");
    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImports(
            "import dagger.internal.Factory;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(expected,
        "public final class InjectConstructor_Factory implements Factory<InjectConstructor> {",
        "  private final Provider<other.pkg.Factory> factoryProvider;",
        "",
        "  public InjectConstructor_Factory(Provider<other.pkg.Factory> factoryProvider) {",
        "    this.factoryProvider = factoryProvider;",
        "  }",
        "",
        "  @Override",
        "  public InjectConstructor get() {",
        "    return newInstance(factoryProvider.get());",
        "  }",
        "",
        "  public static InjectConstructor_Factory create(Provider<other.pkg.Factory> factoryProvider) {",
        "    return new InjectConstructor_Factory(factoryProvider);",
        "  }",
        "",
        "  public static InjectConstructor newInstance(other.pkg.Factory factory) {",
        "    return new InjectConstructor(factory);",
        "  }",
        "}");
    assertAbout(javaSources()).that(List.of(factoryFile, file))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.InjectConstructor_Factory", expected);
  }

  @Test
  void nestedNameCollision() {
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("other.pkg.Outer",
        "package other.pkg;",
        "",
        "public class Outer {",
        "  public class Factory {}",
        "}");
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "import other.pkg.Outer;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(Outer.Factory factory) {}",
        "}");
    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImports(
            "import dagger.internal.Factory;",
            "import jakarta.inject.Provider;",
            "import other.pkg.Outer;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(expected,
        "public final class InjectConstructor_Factory implements Factory<InjectConstructor> {",
        "  private final Provider<Outer.Factory> factoryProvider;",
        "",
        "  public InjectConstructor_Factory(Provider<Outer.Factory> factoryProvider) {",
        "    this.factoryProvider = factoryProvider;",
        "  }",
        "",
        "  @Override",
        "  public InjectConstructor get() {",
        "    return newInstance(factoryProvider.get());",
        "  }",
        "",
        "  public static InjectConstructor_Factory create(Provider<Outer.Factory> factoryProvider) {",
        "    return new InjectConstructor_Factory(factoryProvider);",
        "  }",
        "",
        "  public static InjectConstructor newInstance(Outer.Factory factory) {",
        "    return new InjectConstructor(factory);",
        "  }",
        "}");
    assertAbout(javaSources()).that(List.of(factoryFile, file))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.InjectConstructor_Factory", expected);
  }

  @Test
  void samePackageNameCollision() {
    JavaFileObject samePackageInterface = JavaFileObjects.forSourceLines("test.CommonName",
        "package test;",
        "",
        "public interface CommonName {}");
    JavaFileObject differentPackageInterface = JavaFileObjects.forSourceLines(
        "other.pkg.CommonName",
        "package other.pkg;",
        "",
        "public interface CommonName {}");
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class InjectConstructor implements CommonName {",
        "  @Inject InjectConstructor(other.pkg.CommonName otherPackage, CommonName samePackage) {}",
        "}");
    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImports(
            "import dagger.internal.Factory;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(expected,
        "public final class InjectConstructor_Factory implements Factory<InjectConstructor> {",
        "  private final Provider<other.pkg.CommonName> otherPackageProvider;",
        "  private final Provider<CommonName> samePackageProvider;",
        "",
        "  public InjectConstructor_Factory(Provider<other.pkg.CommonName> otherPackageProvider,",
        "      Provider<CommonName> samePackageProvider) {",
        "    this.otherPackageProvider = otherPackageProvider;",
        "    this.samePackageProvider = samePackageProvider;",
        "  }",
        "",
        "  @Override",
        "  public InjectConstructor get() {",
        "    return newInstance(otherPackageProvider.get(), samePackageProvider.get());",
        "  }",
        "",
        "  public static InjectConstructor_Factory create(",
        "      Provider<other.pkg.CommonName> otherPackageProvider,",
        "      Provider<CommonName> samePackageProvider) {",
        "    return new InjectConstructor_Factory(otherPackageProvider, samePackageProvider);",
        "  }",
        "",
        "  public static InjectConstructor newInstance(other.pkg.CommonName otherPackage,",
        "      CommonName samePackage) {",
        "    return new InjectConstructor(otherPackage, samePackage);",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(List.of(samePackageInterface, differentPackageInterface, file))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.InjectConstructor_Factory", expected);
  }

  @Test
  void noDeps() {
    JavaFileObject simpleType = JavaFileObjects.forSourceLines("test.SimpleType",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class SimpleType {",
        "  @Inject SimpleType() {}",
        "}");
    List<String> factory = new ArrayList<>();
    Collections.addAll(factory,
        "package test;");
    Collections.addAll(factory,
        GeneratedLines.generatedImports("import dagger.internal.Factory;"));
    Collections.addAll(factory,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(factory,
        "public final class SimpleType_Factory implements Factory<SimpleType> {",
        "  @Override",
        "  public SimpleType get() {",
        "    return newInstance();",
        "  }",
        "",
        "  public static SimpleType_Factory create() {",
        "    return InstanceHolder.INSTANCE;",
        "  }",
        "",
        "  public static SimpleType newInstance() {",
        "    return new SimpleType();",
        "  }",
        "",
        "  private static final class InstanceHolder {",
        "    private static final SimpleType_Factory INSTANCE = new SimpleType_Factory();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(simpleType)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.SimpleType_Factory", factory);
  }

  @Test
  void simpleComponentWithNesting() {
    JavaFileObject nestedTypesFile = JavaFileObjects.forSourceLines("test.OuterType",
        "package test;",
        "",
        "import dagger.Component;",
        "import jakarta.inject.Inject;",
        "",
        "final class OuterType {",
        "  static class A {",
        "    @Inject A() {}",
        "  }",
        "  static class B {",
        "    @Inject B(A a) {}",
        "  }",
        "}");
    List<String> aFactory = new ArrayList<>();
    Collections.addAll(aFactory,
        "package test;");
    Collections.addAll(aFactory,
        GeneratedLines.generatedImports("import dagger.internal.Factory;"));
    Collections.addAll(aFactory,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(aFactory,
        "public final class OuterType_A_Factory implements Factory<OuterType.A> {",
        "  @Override",
        "  public OuterType.A get() {",
        "    return newInstance();",
        "  }",
        "",
        "  public static OuterType_A_Factory create() {",
        "    return InstanceHolder.INSTANCE;",
        "  }",
        "",
        "  public static OuterType.A newInstance() {",
        "    return new OuterType.A();",
        "  }",
        "",
        "  private static final class InstanceHolder {",
        "    private static final OuterType_A_Factory INSTANCE = new OuterType_A_Factory();",
        "  }",
        "}");
    assertAbout(javaSources()).that(List.of(nestedTypesFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.OuterType_A_Factory", aFactory);
  }
}
