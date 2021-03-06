/*
 * Copyright (C) 2016 The Dagger Authors.
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
import static io.jbock.common.truth.Truth.assertAbout;
import static io.jbock.testing.compile.CompilationSubject.assertThat;

import dagger.Module;
import io.jbock.common.truth.FailureMetadata;
import io.jbock.common.truth.Subject;
import io.jbock.common.truth.Truth;
import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;

/** A {@link Truth} subject for testing Dagger module methods. */
final class DaggerModuleMethodSubject extends Subject {

  /** A {@link Truth} subject factory for testing Dagger module methods. */
  static final class Factory implements Subject.Factory<DaggerModuleMethodSubject, String> {

    /** Starts a clause testing a Dagger {@link Module @Module} method. */
    static DaggerModuleMethodSubject assertThatModuleMethod(String method) {
      return assertAbout(daggerModuleMethod())
          .that(method)
          .withDeclaration("@Module abstract class %s { %s }");
    }

    /** Starts a clause testing a method in an unannotated class. */
    static DaggerModuleMethodSubject assertThatMethodInUnannotatedClass(String method) {
      return assertAbout(daggerModuleMethod())
          .that(method)
          .withDeclaration("abstract class %s { %s }");
    }

    static Factory daggerModuleMethod() {
      return new Factory();
    }

    private Factory() {
    }

    @Override
    public DaggerModuleMethodSubject createSubject(FailureMetadata failureMetadata, String that) {
      return new DaggerModuleMethodSubject(failureMetadata, that);
    }
  }

  private final String actual;
  private final List<String> imports =
      new ArrayList<>(Arrays.asList(
          // explicitly import Module so it's not ambiguous with java.lang.Module
          "import dagger.Module;",
          "import dagger.*;",
          "import java.util.*;",
          "import jakarta.inject.*;"));
  private String declaration;
  private List<JavaFileObject> additionalSources = List.of();

  private DaggerModuleMethodSubject(FailureMetadata failureMetadata, String subject) {
    super(failureMetadata, subject);
    this.actual = subject;
  }

  /**
   * Imports classes and interfaces. Note that all types in the following packages are already
   * imported:<ul>
   * <li>{@code dagger.*}
   * <li>{@code dagger.multibindings.*}
   * <li>(@code dagger.producers.*}
   * <li>{@code java.util.*}
   * <li>{@code javax.inject.*}
   * </ul>
   */
  DaggerModuleMethodSubject importing(Class<?>... imports) {
    return importing(Arrays.asList(imports));
  }

  /**
   * Imports classes and interfaces. Note that all types in the following packages are already
   * imported:<ul>
   * <li>{@code dagger.*}
   * <li>{@code dagger.multibindings.*}
   * <li>(@code dagger.producers.*}
   * <li>{@code java.util.*}
   * <li>{@code javax.inject.*}
   * </ul>
   */
  DaggerModuleMethodSubject importing(List<? extends Class<?>> imports) {
    imports.stream()
        .map(clazz -> String.format("import %s;", clazz.getCanonicalName()))
        .forEachOrdered(this.imports::add);
    return this;
  }

  /**
   * Sets the declaration of the module. Must be a string with two {@code %s} parameters. The first
   * will be replaced with the name of the type, and the second with the method declaration, which
   * must be within paired braces.
   */
  DaggerModuleMethodSubject withDeclaration(String declaration) {
    this.declaration = declaration;
    return this;
  }

  /** Additional source files that must be compiled with the module. */
  DaggerModuleMethodSubject withAdditionalSources(JavaFileObject... sources) {
    this.additionalSources = Arrays.asList(sources);
    return this;
  }

  /**
   * Fails if compiling the module with the method doesn't report an error at the method
   * declaration whose message contains {@code errorSubstring}.
   */
  void hasError(String errorSubstring) {
    String source = moduleSource();
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule", source);
    Compilation compilation =
        daggerCompiler().compile(Stream.concat(
                additionalSources.stream(),
                Stream.of(module))
            .collect(Collectors.toList()));
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(errorSubstring)
        .inFile(module)
        .onLine(methodLine(source));
  }

  private int methodLine(String source) {
    String beforeMethod = source.substring(0, source.indexOf(actual));
    int methodLine = 1;
    for (int nextNewlineIndex = beforeMethod.indexOf('\n');
         nextNewlineIndex >= 0;
         nextNewlineIndex = beforeMethod.indexOf('\n', nextNewlineIndex + 1)) {
      methodLine++;
    }
    return methodLine;
  }

  private String moduleSource() {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    writer.println("package test;");
    writer.println();
    for (String importLine : imports) {
      writer.println(importLine);
    }
    writer.println();
    writer.printf(declaration, "TestModule", "\n" + actual + "\n");
    writer.println();
    return stringWriter.toString();
  }

}
