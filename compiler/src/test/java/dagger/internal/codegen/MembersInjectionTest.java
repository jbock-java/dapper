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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.internal.codegen.base.Util;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MembersInjectionTest {

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void parentClass_noInjectedMembers(CompilerMode compilerMode) {
    JavaFileObject childFile = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "public final class Child extends Parent {",
        "  @Inject Child() {}",
        "}");
    JavaFileObject parentFile = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "public abstract class Parent {}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  Child child();",
        "}");

    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {",
        "  @Override",
        "  public Child child() {",
        "    return new Child();",
        "  }",
        "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(childFile, parentFile, componentFile);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void parentClass_injectedMembersInSupertype(CompilerMode compilerMode) {
    JavaFileObject childFile = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "public final class Child extends Parent {",
        "  @Inject Child() {}",
        "}");
    JavaFileObject parentFile = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "public abstract class Parent {",
        "  @Inject Dep dep;",
        "}");
    JavaFileObject depFile = JavaFileObjects.forSourceLines("test.Dep",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class Dep {",
        "  @Inject Dep() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  Child child();",
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
        "  @Override",
        "  public Child child() {",
        "    return injectChild(Child_Factory.newInstance());",
        "  }",
        "",
        "  private Child injectChild(Child instance) {",
        "    Parent_MembersInjector.injectDep(instance, new Dep());",
        "    return instance;",
        "  }",
        "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(childFile, parentFile, depFile, componentFile);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void fieldAndMethodGenerics(CompilerMode compilerMode) {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class GenericClass<A, B> {",
        "  @Inject A a;",
        "",
        "  @Inject GenericClass() {}",
        "",
        " @Inject void register(B b) {}",
        "}");

    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(expected,
        "public final class GenericClass_MembersInjector<A, B> implements MembersInjector<GenericClass<A, B>> {",
        "  private final Provider<A> aProvider;",
        "  private final Provider<B> bProvider;",
        "",
        "  public GenericClass_MembersInjector(Provider<A> aProvider, Provider<B> bProvider) {",
        "    this.aProvider = aProvider;",
        "    this.bProvider = bProvider;",
        "  }",
        "",
        "  public static <A, B> MembersInjector<GenericClass<A, B>> create(Provider<A> aProvider,",
        "      Provider<B> bProvider) {",
        "    return new GenericClass_MembersInjector<A, B>(aProvider, bProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(GenericClass<A, B> instance) {",
        "    injectA(instance, aProvider.get());",
        "    injectRegister(instance, bProvider.get());",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.GenericClass.a\")",
        "  public static <A, B> void injectA(Object instance, A a) {",
        "    ((GenericClass<A, B>) instance).a = a;",
        "  }",
        "",
        "  public static <A, B> void injectRegister(Object instance, B b) {",
        "    ((GenericClass<A, B>) instance).register(b);",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.GenericClass_MembersInjector", expected);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void subclassedGenericMembersInjectors(CompilerMode compilerMode) {
    JavaFileObject a = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A() {}",
        "}");
    JavaFileObject a2 = JavaFileObjects.forSourceLines("test.A2",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class A2 {",
        "  @Inject A2() {}",
        "}");
    JavaFileObject parent = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class Parent<X, Y> {",
        "  @Inject X x;",
        "  @Inject Y y;",
        "  @Inject A2 a2;",
        "",
        "  @Inject Parent() {}",
        "}");
    JavaFileObject child = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class Child<T> extends Parent<T, A> {",
        "  @Inject A a;",
        "  @Inject T t;",
        "",
        "  @Inject Child() {}",
        "}");
    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(expected,
        "public final class Child_MembersInjector<T> implements MembersInjector<Child<T>> {",
        "  private final Provider<T> xProvider;",
        "  private final Provider<A> yProvider;",
        "  private final Provider<A2> a2Provider;",
        "  private final Provider<A> aProvider;",
        "  private final Provider<T> tProvider;",
        "",
        "  public Child_MembersInjector(Provider<T> xProvider, Provider<A> yProvider,",
        "      Provider<A2> a2Provider, Provider<A> aProvider, Provider<T> tProvider) {",
        "    this.xProvider = xProvider;",
        "    this.yProvider = yProvider;",
        "    this.a2Provider = a2Provider;",
        "    this.aProvider = aProvider;",
        "    this.tProvider = tProvider;",
        "  }",
        "",
        "  public static <T> MembersInjector<Child<T>> create(Provider<T> xProvider, Provider<A> yProvider,",
        "      Provider<A2> a2Provider, Provider<A> aProvider, Provider<T> tProvider) {",
        "    return new Child_MembersInjector<T>(xProvider, yProvider, a2Provider, aProvider, tProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(Child<T> instance) {",
        "    Parent_MembersInjector.injectX(instance, xProvider.get());",
        "    Parent_MembersInjector.injectY(instance, yProvider.get());",
        "    Parent_MembersInjector.injectA2(instance, a2Provider.get());",
        "    injectA(instance, aProvider.get());",
        "    injectT(instance, tProvider.get());",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.Child.a\")",
        "  public static <T> void injectA(Object instance, Object a) {",
        "    ((Child<T>) instance).a = (A) a;",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.Child.t\")",
        "  public static <T> void injectT(Object instance, T t) {",
        "    ((Child<T>) instance).t = t;",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(List.of(a, a2, parent, child))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.Child_MembersInjector", expected);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void fieldInjection(CompilerMode compilerMode) {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.FieldInjection",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Provider;",
        "",
        "class FieldInjection {",
        "  @Inject String string;",
        "  @Inject Lazy<String> lazyString;",
        "  @Inject Provider<String> stringProvider;",
        "}");

    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.Lazy;",
            "import dagger.MembersInjector;",
            "import dagger.internal.DoubleCheck;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(expected,
        "public final class FieldInjection_MembersInjector implements MembersInjector<FieldInjection> {",
        "  private final Provider<String> stringProvider;",
        "  private final Provider<String> stringProvider2;",
        "  private final Provider<String> stringProvider3;",
        "",
        "  public FieldInjection_MembersInjector(Provider<String> stringProvider,",
        "      Provider<String> stringProvider2, Provider<String> stringProvider3) {",
        "    this.stringProvider = stringProvider;",
        "    this.stringProvider2 = stringProvider2;",
        "    this.stringProvider3 = stringProvider3;",
        "  }",
        "",
        "  public static MembersInjector<FieldInjection> create(Provider<String> stringProvider,",
        "      Provider<String> stringProvider2, Provider<String> stringProvider3) {",
        "    return new FieldInjection_MembersInjector(stringProvider, stringProvider2, stringProvider3);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(FieldInjection instance) {",
        "    injectString(instance, stringProvider.get());",
        "    injectLazyString(instance, DoubleCheck.lazy(stringProvider2));",
        "    injectStringProvider(instance, stringProvider3);",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.FieldInjection.string\")",
        "  public static void injectString(Object instance, String string) {",
        "    ((FieldInjection) instance).string = string;",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.FieldInjection.lazyString\")",
        "  public static void injectLazyString(Object instance, Lazy<String> lazyString) {",
        "    ((FieldInjection) instance).lazyString = lazyString;",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.FieldInjection.stringProvider\")",
        "  public static void injectStringProvider(Object instance, Provider<String> stringProvider) {",
        "    ((FieldInjection) instance).stringProvider = stringProvider;",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.FieldInjection_MembersInjector", expected);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void fieldInjectionWithQualifier(CompilerMode compilerMode) {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.FieldInjectionWithQualifier",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Named;",
            "import jakarta.inject.Provider;",
            "",
            "class FieldInjectionWithQualifier {",
            "  @Inject @Named(\"A\") String a;",
            "  @Inject @Named(\"B\") String b;",
            "}");

    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Named;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(expected,
        "public final class FieldInjectionWithQualifier_MembersInjector implements MembersInjector<FieldInjectionWithQualifier> {",
        "  private final Provider<String> aProvider;",
        "  private final Provider<String> bProvider;",
        "",
        "  public FieldInjectionWithQualifier_MembersInjector(Provider<String> aProvider,",
        "      Provider<String> bProvider) {",
        "    this.aProvider = aProvider;",
        "    this.bProvider = bProvider;",
        "  }",
        "",
        "  public static MembersInjector<FieldInjectionWithQualifier> create(Provider<String> aProvider,",
        "      Provider<String> bProvider) {",
        "    return new FieldInjectionWithQualifier_MembersInjector(aProvider, bProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(FieldInjectionWithQualifier instance) {",
        "    injectA(instance, aProvider.get());",
        "    injectB(instance, bProvider.get());",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.FieldInjectionWithQualifier.a\")",
        "  @Named(\"A\")",
        "  public static void injectA(Object instance, String a) {",
        "    ((FieldInjectionWithQualifier) instance).a = a;",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.FieldInjectionWithQualifier.b\")",
        "  @Named(\"B\")",
        "  public static void injectB(Object instance, String b) {",
        "    ((FieldInjectionWithQualifier) instance).b = b;",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.FieldInjectionWithQualifier_MembersInjector", expected);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void methodInjection(CompilerMode compilerMode) {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MethodInjection",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Provider;",
        "",
        "class MethodInjection {",
        "  @Inject void noArgs() {}",
        "  @Inject void oneArg(String string) {}",
        "  @Inject void manyArgs(",
        "      String string, Lazy<String> lazyString, Provider<String> stringProvider) {}",
        "}");

    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "package test;");
    Collections.addAll(expected,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.Lazy;",
            "import dagger.MembersInjector;",
            "import dagger.internal.DoubleCheck;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expected,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(expected,
        "public final class MethodInjection_MembersInjector implements MembersInjector<MethodInjection> {",
        "  private final Provider<String> stringProvider;",
        "  private final Provider<String> stringProvider2;",
        "  private final Provider<String> stringProvider3;",
        "  private final Provider<String> stringProvider4;",
        "",
        "  public MethodInjection_MembersInjector(Provider<String> stringProvider,",
        "      Provider<String> stringProvider2, Provider<String> stringProvider3,",
        "      Provider<String> stringProvider4) {",
        "    this.stringProvider = stringProvider;",
        "    this.stringProvider2 = stringProvider2;",
        "    this.stringProvider3 = stringProvider3;",
        "    this.stringProvider4 = stringProvider4;",
        "  }",
        "",
        "  public static MembersInjector<MethodInjection> create(Provider<String> stringProvider,",
        "      Provider<String> stringProvider2, Provider<String> stringProvider3,",
        "      Provider<String> stringProvider4) {",
        "    return new MethodInjection_MembersInjector(stringProvider, stringProvider2, stringProvider3, stringProvider4);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(MethodInjection instance) {",
        "    injectNoArgs(instance);",
        "    injectOneArg(instance, stringProvider.get());",
        "    injectManyArgs(instance, stringProvider2.get(), DoubleCheck.lazy(stringProvider3), stringProvider4);",
        "  }",
        "",
        "  public static void injectNoArgs(Object instance) {",
        "    ((MethodInjection) instance).noArgs();",
        "  }",
        "",
        "  public static void injectOneArg(Object instance, String string) {",
        "    ((MethodInjection) instance).oneArg(string);",
        "  }",
        "",
        "  public static void injectManyArgs(Object instance, String string, Lazy<String> lazyString,",
        "      Provider<String> stringProvider) {",
        "    ((MethodInjection) instance).manyArgs(string, lazyString, stringProvider);",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.MethodInjection_MembersInjector", expected);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void mixedMemberInjection(CompilerMode compilerMode) {
    JavaFileObject file = JavaFileObjects.forSourceLines(
        "test.MixedMemberInjection",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Provider;",
        "",
        "class MixedMemberInjection {",
        "  @Inject String string;",
        "  @Inject void setString(String s) {}",
        "  @Inject Object object;",
        "  @Inject void setObject(Object o) {}",
        "}");

    List<String> expected = new ArrayList<>();
    Collections.addAll(expected,
        "");
    Collections.addAll(expected,
        "");
    Collections.addAll(expected,
        "");
    Collections.addAll(expected,
        "");
    JavaFileObjects.forSourceLines(
        "test.MixedMemberInjection_MembersInjector",
        "package test;",
        "",
        GeneratedLines.generatedImports(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"),
        "",
        GeneratedLines.generatedAnnotations(),
        "public final class MixedMemberInjection_MembersInjector implements MembersInjector<MixedMemberInjection> {",
        "  private final Provider<String> stringProvider;",
        "  private final Provider<Object> objectProvider;",
        "  private final Provider<String> sProvider;",
        "  private final Provider<Object> oProvider;",
        "",
        "  public MixedMemberInjection_MembersInjector(Provider<String> stringProvider,",
        "      Provider<Object> objectProvider, Provider<String> sProvider, Provider<Object> oProvider) {",
        "    this.stringProvider = stringProvider;",
        "    this.objectProvider = objectProvider;",
        "    this.sProvider = sProvider;",
        "    this.oProvider = oProvider;",
        "  }",
        "",
        "  public static MembersInjector<MixedMemberInjection> create(Provider<String> stringProvider,",
        "      Provider<Object> objectProvider, Provider<String> sProvider, Provider<Object> oProvider) {",
        "    return new MixedMemberInjection_MembersInjector(stringProvider, objectProvider, sProvider, oProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(MixedMemberInjection instance) {",
        "    injectString(instance, stringProvider.get());",
        "    injectObject(instance, objectProvider.get());",
        "    injectSetString(instance, sProvider.get());",
        "    injectSetObject(instance, oProvider.get());",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.MixedMemberInjection.string\")",
        "  public static void injectString(Object instance, String string) {",
        "    ((MixedMemberInjection) instance).string = string;",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.MixedMemberInjection.object\")",
        "  public static void injectObject(Object instance, Object object) {",
        "    ((MixedMemberInjection) instance).object = object;",
        "  }",
        "",
        "  public static void injectSetString(Object instance, String s) {",
        "    ((MixedMemberInjection) instance).setString(s);",
        "  }",
        "",
        "  public static void injectSetObject(Object instance, Object o) {",
        "    ((MixedMemberInjection) instance).setObject(o);",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.MixedMemberInjection_MembersInjector", expected);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void injectConstructorAndMembersInjection(CompilerMode compilerMode) {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.AllInjections",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class AllInjections {",
        "  @Inject String s;",
        "  @Inject AllInjections(String s) {}",
        "  @Inject void s(String s) {}",
        "}");

    List<String> expectedMembersInjector = new ArrayList<>();
    Collections.addAll(expectedMembersInjector,
        "package test;");
    Collections.addAll(expectedMembersInjector,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expectedMembersInjector,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(expectedMembersInjector,
        "public final class AllInjections_MembersInjector implements MembersInjector<AllInjections> {",
        "  private final Provider<String> sProvider;",
        "  private final Provider<String> sProvider2;",
        "",
        "  public AllInjections_MembersInjector(Provider<String> sProvider, Provider<String> sProvider2) {",
        "    this.sProvider = sProvider;",
        "    this.sProvider2 = sProvider2;",
        "  }",
        "",
        "  public static MembersInjector<AllInjections> create(Provider<String> sProvider,",
        "      Provider<String> sProvider2) {",
        "    return new AllInjections_MembersInjector(sProvider, sProvider2);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(AllInjections instance) {",
        "    injectS(instance, sProvider.get());",
        "    injectS2(instance, sProvider2.get());",
        "  }",
        "",
        // TODO(b/64477506): now that these all take "object", it would be nice to rename
        // "instance"
        // to the type name
        "  @InjectedFieldSignature(\"test.AllInjections.s\")",
        "  public static void injectS(Object instance, String s) {",
        "    ((AllInjections) instance).s = s;",
        "  }",
        "",
        "  public static void injectS2(Object instance, String s) {",
        "    ((AllInjections) instance).s(s);",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.AllInjections_MembersInjector", expectedMembersInjector);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void supertypeMembersInjection(CompilerMode compilerMode) {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "class A {}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "class B extends A {",
        "  @Inject String s;",
        "}");
    List<String> expectedMembersInjector = new ArrayList<>();
    Collections.addAll(expectedMembersInjector,
        "package test;");
    Collections.addAll(expectedMembersInjector,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expectedMembersInjector,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(expectedMembersInjector,
        "public final class B_MembersInjector implements MembersInjector<B> {",
        "  private final Provider<String> sProvider;",
        "",
        "  public B_MembersInjector(Provider<String> sProvider) {",
        "    this.sProvider = sProvider;",
        "  }",
        "",
        "  public static MembersInjector<B> create(Provider<String> sProvider) {",
        "    return new B_MembersInjector(sProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(B instance) {",
        "    injectS(instance, sProvider.get());",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.B.s\")",
        "  public static void injectS(Object instance, String s) {",
        "    ((B) instance).s = s;",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(List.of(aFile, bFile))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.B_MembersInjector", expectedMembersInjector);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void simpleComponentWithNesting(CompilerMode compilerMode) {
    JavaFileObject nestedTypesFile = JavaFileObjects.forSourceLines(
        "test.OuterType",
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
        "    @Inject A a;",
        "  }",
        "  @Component interface SimpleComponent {",
        "    A a();",
        "    void inject(B b);",
        "  }",
        "}");

    List<String> bMembersInjector = new ArrayList<>();
    Collections.addAll(bMembersInjector,
        "package test;");
    Collections.addAll(bMembersInjector,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(bMembersInjector,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(bMembersInjector,
        "public final class OuterType_B_MembersInjector implements MembersInjector<OuterType.B> {",
        "  private final Provider<OuterType.A> aProvider;",
        "",
        "  public OuterType_B_MembersInjector(Provider<OuterType.A> aProvider) {",
        "    this.aProvider = aProvider;",
        "  }",
        "",
        "  public static MembersInjector<OuterType.B> create(Provider<OuterType.A> aProvider) {",
        "    return new OuterType_B_MembersInjector(aProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(OuterType.B instance) {",
        "    injectA(instance, aProvider.get());",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.OuterType.B.a\")",
        "  public static void injectA(Object instance, Object a) {",
        "    ((OuterType.B) instance).a = (OuterType.A) a;",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(List.of(nestedTypesFile))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.OuterType_B_MembersInjector", bMembersInjector);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void componentWithNestingAndGeneratedType(CompilerMode compilerMode) {
    JavaFileObject nestedTypesFile =
        JavaFileObjects.forSourceLines(
            "test.OuterType",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Inject;",
            "",
            "final class OuterType {",
            "  @Inject GeneratedType generated;",
            "  static class A {",
            "    @Inject A() {}",
            "  }",
            "  static class B {",
            "    @Inject A a;",
            "  }",
            "  @Component interface SimpleComponent {",
            "    A a();",
            "    void inject(B b);",
            "  }",
            "}");

    List<String> bMembersInjector = new ArrayList<>();
    Collections.addAll(bMembersInjector,
        "package test;");
    Collections.addAll(bMembersInjector,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(bMembersInjector,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(bMembersInjector,
        "public final class OuterType_B_MembersInjector implements MembersInjector<OuterType.B> {",
        "  private final Provider<OuterType.A> aProvider;",
        "",
        "  public OuterType_B_MembersInjector(Provider<OuterType.A> aProvider) {",
        "    this.aProvider = aProvider;",
        "  }",
        "",
        "  public static MembersInjector<OuterType.B> create(Provider<OuterType.A> aProvider) {",
        "    return new OuterType_B_MembersInjector(aProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(OuterType.B instance) {",
        "    injectA(instance, aProvider.get());",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.OuterType.B.a\")",
        "  public static void injectA(Object instance, Object a) {",
        "    ((OuterType.B) instance).a = (OuterType.A) a;",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(nestedTypesFile)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(List.of(
            new ComponentProcessor(),
            new AbstractProcessor() {
              private boolean done;

              @Override
              public Set<String> getSupportedAnnotationTypes() {
                return Set.of("*");
              }

              @Override
              public boolean process(
                  Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                if (!done) {
                  done = true;
                  try (Writer writer =
                           processingEnv
                               .getFiler()
                               .createSourceFile("test.GeneratedType")
                               .openWriter()) {
                    writer.write(
                        String.join("\n",
                            Arrays.asList(
                                "package test;",
                                "",
                                "import jakarta.inject.Inject;",
                                "",
                                "class GeneratedType {",
                                "  @Inject GeneratedType() {}",
                                "}")));
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                }
                return false;
              }
            }))
        .compilesWithoutError()
        .and()
        .containsLines("test.OuterType_B_MembersInjector", bMembersInjector);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void lowerCaseNamedMembersInjector_forLowerCaseType(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.foo",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class foo {",
            "  @Inject String string;",
            "}");
    JavaFileObject fooModule =
        JavaFileObjects.forSourceLines(
            "test.fooModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class fooModule {",
            "  @Provides String string() { return \"foo\"; }",
            "}");
    JavaFileObject fooComponent =
        JavaFileObjects.forSourceLines(
            "test.fooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = fooModule.class)",
            "interface fooComponent {",
            "  void inject(foo target);",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(foo, fooModule, fooComponent);
    assertThat(compilation).succeeded();
    assertThat(compilation).generatedFile(CLASS_OUTPUT, "test", "foo_MembersInjector.class");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void fieldInjectionForShadowedMember(CompilerMode compilerMode) {
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
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Parent { ",
            "  @Inject Foo object;",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Child extends Parent { ",
            "  @Inject Bar object;",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface C { ",
            "  void inject(Child child);",
            "}");

    List<String> expectedMembersInjector = new ArrayList<>();
    Collections.addAll(expectedMembersInjector,
        "package test;");
    Collections.addAll(expectedMembersInjector,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expectedMembersInjector,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(expectedMembersInjector,
        "public final class Child_MembersInjector implements MembersInjector<Child> {",
        "  private final Provider<Foo> objectProvider;",
        "  private final Provider<Bar> objectProvider2;",
        "",
        "  public Child_MembersInjector(Provider<Foo> objectProvider, Provider<Bar> objectProvider2) {",
        "    this.objectProvider = objectProvider;",
        "    this.objectProvider2 = objectProvider2;",
        "  }",
        "",
        "  public static MembersInjector<Child> create(Provider<Foo> objectProvider,",
        "      Provider<Bar> objectProvider2) {",
        "    return new Child_MembersInjector(objectProvider, objectProvider2);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(Child instance) {",
        "    Parent_MembersInjector.injectObject(instance, objectProvider.get());",
        "    injectObject(instance, objectProvider2.get());",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.Child.object\")",
        "  public static void injectObject(Object instance, Object object) {",
        "    ((Child) instance).object = (Bar) object;",
        "  }",
        "}");

    assertAbout(javaSources())
        .that(List.of(foo, bar, parent, child, component))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.Child_MembersInjector", expectedMembersInjector);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void privateNestedClassError(CompilerMode compilerMode) {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static final class InnerClass {",
        "    @Inject int field;",
        "  }",
        "}");
    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(6);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void privateNestedClassWarning(CompilerMode compilerMode) {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static final class InnerClass {",
        "    @Inject int field;",
        "  }",
        "}");
    Compilation compilation =
        compilerWithOptions(
            Util.concat(compilerMode.javacopts(), List.of("-Adagger.privateMemberValidation=WARNING")))
            .compile(file);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(6);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void privateSuperclassIsOkIfNotInjectedInto(CompilerMode compilerMode) {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static class BaseClass {}",
        "",
        "  static final class DerivedClass extends BaseClass {",
        "    @Inject int field;",
        "  }",
        "}");
    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).succeeded();
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void rawFrameworkTypeField(CompilerMode compilerMode) {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.RawFrameworkTypes",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Provider;",
            "",
            "class RawProviderField {",
            "  @Inject Provider fieldWithRawProvider;",
            "}",
            "",
            "@Component",
            "interface C {",
            "  void inject(RawProviderField rawProviderField);",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Provider cannot be provided")
        .inFile(file)
        .onLineContaining("interface C");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void throwExceptionInjectedMethod(CompilerMode compilerMode) {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Inject;",
            "class SomeClass {",
            "@Inject void inject() throws Exception {}",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Methods with @Inject may not throw checked exceptions. "
            + "Please wrap your exceptions in a RuntimeException instead.")
        .inFile(file)
        .onLineContaining("throws Exception");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void rawFrameworkTypeParameter(CompilerMode compilerMode) {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.RawFrameworkTypes",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Provider;",
            "",
            "class RawProviderParameter {",
            "  @Inject void methodInjection(Provider rawProviderParameter) {}",
            "}",
            "",
            "@Component",
            "interface C {",
            "  void inject(RawProviderParameter rawProviderParameter);",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Provider cannot be provided")
        .inFile(file)
        .onLineContaining("interface C");
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void injectsPrimitive(CompilerMode compilerMode) {
    JavaFileObject injectedType =
        JavaFileObjects.forSourceLines(
            "test.InjectedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class InjectedType {",
            "  @Inject InjectedType() {}",
            "",
            "  @Inject int primitiveInt;",
            "  @Inject Integer boxedInt;",
            "}");

    List<String> membersInjector = new ArrayList<>();
    Collections.addAll(membersInjector,
        "package test;");
    Collections.addAll(membersInjector,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(membersInjector,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(membersInjector,
        "public final class InjectedType_MembersInjector implements MembersInjector<InjectedType> {",
        "  private final Provider<Integer> primitiveIntProvider;",
        "  private final Provider<Integer> boxedIntProvider;",
        "",
        "  public InjectedType_MembersInjector(Provider<Integer> primitiveIntProvider,",
        "      Provider<Integer> boxedIntProvider) {",
        "    this.primitiveIntProvider = primitiveIntProvider;",
        "    this.boxedIntProvider = boxedIntProvider;",
        "  }",
        "",
        "  public static MembersInjector<InjectedType> create(Provider<Integer> primitiveIntProvider,",
        "      Provider<Integer> boxedIntProvider) {",
        "    return new InjectedType_MembersInjector(primitiveIntProvider, boxedIntProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(InjectedType instance) {",
        "    injectPrimitiveInt(instance, primitiveIntProvider.get());",
        "    injectBoxedInt(instance, boxedIntProvider.get());",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.InjectedType.primitiveInt\")",
        "  public static void injectPrimitiveInt(Object instance, int primitiveInt) {",
        "    ((InjectedType) instance).primitiveInt = primitiveInt;",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.InjectedType.boxedInt\")",
        "  public static void injectBoxedInt(Object instance, Integer boxedInt) {",
        "    ((InjectedType) instance).boxedInt = boxedInt;",
        "  }",
        "}");

    List<String> factory = new ArrayList<>();
    Collections.addAll(factory,
        "package test;");
    Collections.addAll(factory,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.internal.Factory;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(factory,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(factory,
        "public final class InjectedType_Factory implements Factory<InjectedType> {",
        "  private final Provider<Integer> primitiveIntProvider;",
        "",
        "  private final Provider<Integer> boxedIntProvider;",
        "",
        "  public InjectedType_Factory(Provider<Integer> primitiveIntProvider,",
        "      Provider<Integer> boxedIntProvider) {",
        "    this.primitiveIntProvider = primitiveIntProvider;",
        "    this.boxedIntProvider = boxedIntProvider;",
        "  }",
        "",
        "  @Override",
        "  public InjectedType get() {",
        "    InjectedType instance = newInstance();",
        "    InjectedType_MembersInjector.injectPrimitiveInt(instance, primitiveIntProvider.get());",
        "    InjectedType_MembersInjector.injectBoxedInt(instance, boxedIntProvider.get());",
        "    return instance;",
        "  }",
        "",
        "  public static InjectedType_Factory create(Provider<Integer> primitiveIntProvider,",
        "      Provider<Integer> boxedIntProvider) {",
        "    return new InjectedType_Factory(primitiveIntProvider, boxedIntProvider);",
        "  }",
        "",
        "  public static InjectedType newInstance() {",
        "    return new InjectedType();",
        "  }",
        "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(injectedType);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.InjectedType_MembersInjector")
        .containsLines(membersInjector);
    assertThat(compilation)
        .generatedSourceFile("test.InjectedType_Factory")
        .containsLines(factory);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void accessibility(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "other.Foo",
            "package other;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject inaccessible =
        JavaFileObjects.forSourceLines(
            "other.Inaccessible",
            "package other;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Inaccessible {",
            "  @Inject Inaccessible() {}",
            "  @Inject Foo foo;",
            "  @Inject void method(Foo foo) {}",
            "}");
    JavaFileObject usesInaccessible =
        JavaFileObjects.forSourceLines(
            "other.UsesInaccessible",
            "package other;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "public class UsesInaccessible {",
            "  @Inject UsesInaccessible(Inaccessible inaccessible) {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import other.UsesInaccessible;",
            "",
            "@Component",
            "interface TestComponent {",
            "  UsesInaccessible usesInaccessible();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(foo, inaccessible, usesInaccessible, component);
    assertThat(compilation).succeeded();

    List<String> inaccessibleMembersInjector = new ArrayList<>();
    Collections.addAll(inaccessibleMembersInjector,
        "package other;");
    Collections.addAll(inaccessibleMembersInjector,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(inaccessibleMembersInjector,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(inaccessibleMembersInjector,
        "public final class Inaccessible_MembersInjector implements MembersInjector<Inaccessible> {",
        "  private final Provider<Foo> fooProvider;",
        "  private final Provider<Foo> fooProvider2;",
        "",
        "  public Inaccessible_MembersInjector(Provider<Foo> fooProvider, Provider<Foo> fooProvider2) {",
        "    this.fooProvider = fooProvider;",
        "    this.fooProvider2 = fooProvider2;",
        "  }",
        "",
        "  public static MembersInjector<Inaccessible> create(Provider<Foo> fooProvider,",
        "      Provider<Foo> fooProvider2) {",
        "    return new Inaccessible_MembersInjector(fooProvider, fooProvider2);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(Inaccessible instance) {",
        "    injectFoo(instance, fooProvider.get());",
        "    injectMethod(instance, fooProvider2.get());",
        "  }",
        "",
        "  @InjectedFieldSignature(\"other.Inaccessible.foo\")",
        "  public static void injectFoo(Object instance, Object foo) {",
        "    ((Inaccessible) instance).foo = (Foo) foo;",
        "  }",
        "",
        "  public static void injectMethod(Object instance, Object foo) {",
        "    ((Inaccessible) instance).method((Foo) foo);",
        "  }",
        "}");
    assertThat(compilation)
        .generatedSourceFile("other.Inaccessible_MembersInjector")
        .containsLines(inaccessibleMembersInjector);

    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedImportsIndividual(
            "import other.Foo_Factory;",
            "import other.Inaccessible_Factory;",
            "import other.Inaccessible_MembersInjector;",
            "import other.UsesInaccessible;",
            "import other.UsesInaccessible_Factory;"));
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {",
        "  private Object inaccessible() {",
        "    return injectInaccessible(Inaccessible_Factory.newInstance());",
        "  }",
        "",
        "  @Override",
        "  public UsesInaccessible usesInaccessible() {",
        "    return UsesInaccessible_Factory.newInstance(inaccessible());",
        "  }",
        "",
        // TODO(ronshapiro): if possible, it would be great to rename "instance", but we
        // need to make sure that this doesn't conflict with any framework field in this or
        // any parent component
        "  private Object injectInaccessible(Object instance) {",
        "    Inaccessible_MembersInjector.injectFoo(instance, Foo_Factory.newInstance());",
        "    Inaccessible_MembersInjector.injectMethod(instance, Foo_Factory.newInstance());",
        "    return instance;",
        "  }",
        "}");
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void accessibleRawType_ofInaccessibleType(CompilerMode compilerMode) {
    JavaFileObject inaccessible =
        JavaFileObjects.forSourceLines(
            "other.Inaccessible",
            "package other;",
            "",
            "class Inaccessible {}");
    JavaFileObject inaccessiblesModule =
        JavaFileObjects.forSourceLines(
            "other.InaccessiblesModule",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.ArrayList;",
            "import java.util.List;",
            "import jakarta.inject.Provider;",
            "import jakarta.inject.Singleton;",
            "",
            "@Module",
            "public class InaccessiblesModule {",
            // force Provider initialization
            "  @Provides @Singleton static List<Inaccessible> inaccessibles() {",
            "    return new ArrayList<>();",
            "  }",
            "}");
    JavaFileObject usesInaccessibles =
        JavaFileObjects.forSourceLines(
            "other.UsesInaccessibles",
            "package other;",
            "",
            "import java.util.List;",
            "import jakarta.inject.Inject;",
            "",
            "public class UsesInaccessibles {",
            "  @Inject UsesInaccessibles() {}",
            "  @Inject List<Inaccessible> inaccessibles;",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Singleton;",
            "import other.UsesInaccessibles;",
            "",
            "@Singleton",
            "@Component(modules = other.InaccessiblesModule.class)",
            "interface TestComponent {",
            "  UsesInaccessibles usesInaccessibles();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(inaccessible, inaccessiblesModule, usesInaccessibles, component);
    assertThat(compilation).succeeded();
    String[] generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;",
                "")
            .addLines(GeneratedLines.generatedImportsIndividual(
                "import other.InaccessiblesModule;",
                "import other.InaccessiblesModule_InaccessiblesFactory;",
                "import other.UsesInaccessibles;",
                "import other.UsesInaccessibles_Factory;",
                "import other.UsesInaccessibles_MembersInjector;"))
            .addLines(GeneratedLines.generatedAnnotationsIndividual())
            .addLines("final class DaggerTestComponent implements TestComponent {")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private volatile Object listOfInaccessible = new MemoizedSentinel();",
                "",
                "  private List listOfInaccessible() {",
                "    Object local = listOfInaccessible;",
                "    if (local instanceof MemoizedSentinel) {",
                "      synchronized (local) {",
                "        local = listOfInaccessible;",
                "        if (local instanceof MemoizedSentinel) {",
                "          local = InaccessiblesModule_InaccessiblesFactory.inaccessibles();",
                "          listOfInaccessible = DoubleCheck.reentrantCheck(listOfInaccessible, local);",
                "        }",
                "      }",
                "    }",
                "    return (List) local;",
                "  }")
            .addLinesIn(
                DEFAULT_MODE,
                "  @SuppressWarnings(\"rawtypes\")",
                "  private Provider inaccessiblesProvider;",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.inaccessiblesProvider = DoubleCheck.provider(InaccessiblesModule_InaccessiblesFactory.create());",
                "  }")
            .addLines(
                "",
                "  @Override",
                "  public UsesInaccessibles usesInaccessibles() {",
                "    return injectUsesInaccessibles(UsesInaccessibles_Factory.newInstance());",
                "  }",
                "")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private UsesInaccessibles injectUsesInaccessibles(UsesInaccessibles instance) {",
                "    UsesInaccessibles_MembersInjector.injectInaccessibles(instance, (List) listOfInaccessible());",
                "    return instance;",
                "  }")
            .addLinesIn(
                DEFAULT_MODE,
                "  private UsesInaccessibles injectUsesInaccessibles(UsesInaccessibles instance) {",
                "    UsesInaccessibles_MembersInjector.injectInaccessibles(instance, (List) inaccessiblesProvider.get());",
                "    return instance;",
                "  }")
            .addLines(
                "}")
            .lines();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void publicSupertypeHiddenSubtype(CompilerMode compilerMode) {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "other.Foo",
            "package other;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "other.Supertype",
            "package other;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "public class Supertype<T> {",
            "  @Inject T t;",
            "}");
    JavaFileObject subtype =
        JavaFileObjects.forSourceLines(
            "other.Subtype",
            "package other;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class Subtype extends Supertype<Foo> {",
            "  @Inject Subtype() {}",
            "}");
    JavaFileObject injectsSubtype =
        JavaFileObjects.forSourceLines(
            "other.InjectsSubtype",
            "package other;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "public class InjectsSubtype {",
            "  @Inject InjectsSubtype(Subtype s) {}",
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
            "  other.InjectsSubtype injectsSubtype();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(foo, supertype, subtype, injectsSubtype, component);
    assertThat(compilation).succeeded();

    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedImportsIndividual(
            "import other.Foo_Factory;",
            "import other.InjectsSubtype;",
            "import other.InjectsSubtype_Factory;",
            "import other.Subtype_Factory;",
            "import other.Supertype;",
            "import other.Supertype_MembersInjector;"));
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {",
        "  private Object subtype() {",
        "    return injectSubtype(Subtype_Factory.newInstance());",
        "  }",
        "",
        "  @Override",
        "  public InjectsSubtype injectsSubtype() {",
        "    return InjectsSubtype_Factory.newInstance(subtype());",
        "  }",
        "",
        "  private Object injectSubtype(Object instance) {",
        "    Supertype_MembersInjector.injectT((Supertype) instance, Foo_Factory.newInstance());",
        "    return instance;",
        "  }",
        "}");

    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  // Shows that we shouldn't create a members injector for a type that doesn't have
  // @Inject fields or @Inject constructor even if it extends and is extended by types that do.
  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void middleClassNoFieldInjection(CompilerMode compilerMode) {
    JavaFileObject classA =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class A extends B {",
            "  @Inject String valueA;",
            "}");
    JavaFileObject classB =
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "class B extends C {",
            "}");
    JavaFileObject classC =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class C { ",
            "  @Inject String valueC;",
            "}");
    List<String> expectedAMembersInjector = new ArrayList<>();
    Collections.addAll(expectedAMembersInjector,
        "package test;");
    Collections.addAll(expectedAMembersInjector,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expectedAMembersInjector,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(expectedAMembersInjector,
        "public final class A_MembersInjector implements MembersInjector<A> {",
        "  private final Provider<String> valueCProvider;",
        "  private final Provider<String> valueAProvider;",
        "",
        "  public A_MembersInjector(Provider<String> valueCProvider, Provider<String> valueAProvider) {",
        "    this.valueCProvider = valueCProvider;",
        "    this.valueAProvider = valueAProvider;",
        "  }",
        "",
        "  public static MembersInjector<A> create(Provider<String> valueCProvider,",
        "      Provider<String> valueAProvider) {",
        "    return new A_MembersInjector(valueCProvider, valueAProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(A instance) {",
        "    C_MembersInjector.injectValueC(instance, valueCProvider.get());",
        "    injectValueA(instance, valueAProvider.get());",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.A.valueA\")",
        "  public static void injectValueA(Object instance, String valueA) {",
        "    ((A) instance).valueA = valueA;",
        "  }",
        "}");

    List<String> expectedCMembersInjector = new ArrayList<>();
    Collections.addAll(expectedCMembersInjector,
        "package test;");
    Collections.addAll(expectedCMembersInjector,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(expectedCMembersInjector,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(expectedCMembersInjector,
        "public final class C_MembersInjector implements MembersInjector<C> {",
        "  private final Provider<String> valueCProvider;",
        "",
        "  public C_MembersInjector(Provider<String> valueCProvider) {",
        "    this.valueCProvider = valueCProvider;",
        "  }",
        "",
        "  public static MembersInjector<C> create(Provider<String> valueCProvider) {",
        "    return new C_MembersInjector(valueCProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(C instance) {",
        "    injectValueC(instance, valueCProvider.get());",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.C.valueC\")",
        "  public static void injectValueC(Object instance, String valueC) {",
        "    ((C) instance).valueC = valueC;",
        "  }",
        "}");


    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(classA, classB, classC);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.A_MembersInjector")
        .containsLines(expectedAMembersInjector);
    assertThat(compilation)
        .generatedSourceFile("test.C_MembersInjector")
        .containsLines(expectedCMembersInjector);

    try {
      assertThat(compilation).generatedSourceFile("test.B_MembersInjector");
      // Can't throw an assertion error since it would be caught.
      throw new IllegalStateException("Test generated a B_MembersInjector");
    } catch (AssertionError expected) {
    }
  }

  // Shows that we do generate a MembersInjector for a type that has an @Inject
  // constructor and that extends a type with @Inject fields, even if it has no local field
  // injection sites
  // TODO(erichang): Are these even used anymore?
  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testConstructorInjectedFieldInjection(CompilerMode compilerMode) {
    JavaFileObject classA =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class A extends B {",
            "  @Inject A() {}",
            "}");
    JavaFileObject classB =
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class B { ",
            "  @Inject String valueB;",
            "}");

    List<String> aMembersInjector = new ArrayList<>();
    Collections.addAll(aMembersInjector,
        "package test;");
    Collections.addAll(aMembersInjector,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(aMembersInjector,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(aMembersInjector,
        "public final class A_MembersInjector implements MembersInjector<A> {",
        "  private final Provider<String> valueBProvider;",
        "",
        "  public A_MembersInjector(Provider<String> valueBProvider) {",
        "    this.valueBProvider = valueBProvider;",
        "  }",
        "",
        "  public static MembersInjector<A> create(Provider<String> valueBProvider) {",
        "    return new A_MembersInjector(valueBProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(A instance) {",
        "    B_MembersInjector.injectValueB(instance, valueBProvider.get());",
        "  }",
        "}");

    List<String> bMembersInjector = new ArrayList<>();
    Collections.addAll(bMembersInjector,
        "package test;");
    Collections.addAll(bMembersInjector,
        GeneratedLines.generatedImportsIndividual(
            "import dagger.MembersInjector;",
            "import dagger.internal.InjectedFieldSignature;",
            "import jakarta.inject.Provider;"));
    Collections.addAll(bMembersInjector,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(bMembersInjector,
        "public final class B_MembersInjector implements MembersInjector<B> {",
        "  private final Provider<String> valueBProvider;",
        "",
        "  public B_MembersInjector(Provider<String> valueBProvider) {",
        "    this.valueBProvider = valueBProvider;",
        "  }",
        "",
        "  public static MembersInjector<B> create(Provider<String> valueBProvider) {",
        "    return new B_MembersInjector(valueBProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(B instance) {",
        "    injectValueB(instance, valueBProvider.get());",
        "  }",
        "",
        "  @InjectedFieldSignature(\"test.B.valueB\")",
        "  public static void injectValueB(Object instance, String valueB) {",
        "    ((B) instance).valueB = valueB;",
        "  }",
        "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(classA, classB);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.A_MembersInjector")
        .containsLines(aMembersInjector);
    assertThat(compilation)
        .generatedSourceFile("test.B_MembersInjector")
        .containsLines(bMembersInjector);
  }
}
