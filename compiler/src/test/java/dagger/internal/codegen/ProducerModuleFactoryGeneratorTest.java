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

// TODO(beder): Merge the error-handling tests with the ModuleFactoryGeneratorTest.
package dagger.internal.codegen;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatMethodInUnannotatedClass;

public class ProducerModuleFactoryGeneratorTest {

  @Test
  public void producesMethodNotInModule() {
    assertThatMethodInUnannotatedClass("@Produces String produceString() { return null; }")
        .hasError("@Produces methods can only be present within a @ProducerModule");
  }

  @Test
  public void multipleProducesMethodsWithSameName() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces Object produce(int i) {",
        "    return i;",
        "  }",
        "",
        "  @Produces String produce() {",
        "    return \"\";",
        "  }",
        "}");
    String errorMessage =
        "Cannot have more than one binding method with the same name in a single module";
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(errorMessage).inFile(moduleFile).onLine(8);
    assertThat(compilation).hadErrorContaining(errorMessage).inFile(moduleFile).onLine(12);
  }

  @Test
  public void privateModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.Enclosing",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "final class Enclosing {",
        "  @ProducerModule private static final class PrivateModule {",
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
  public void enclosedInPrivateModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.Enclosing",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "final class Enclosing {",
        "  private static final class PrivateEnclosing {",
        "    @ProducerModule static final class TestModule {",
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
  public void includesNonModule() {
    JavaFileObject xFile =
        JavaFileObjects.forSourceLines("test.X", "package test;", "", "public final class X {}");
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.FooModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "",
            "@ProducerModule(includes = X.class)",
            "public final class FooModule {",
            "}");
    Compilation compilation = daggerCompiler().compile(xFile, moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "X is listed as a module, but is not annotated with one of @Module, @ProducerModule");
  }

  // TODO(ronshapiro): merge this with the equivalent test in ModuleFactoryGeneratorTest and make it
  // parameterized
  @Test
  public void publicModuleNonPublicIncludes() {
    JavaFileObject publicModuleFile = JavaFileObjects.forSourceLines("test.PublicModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "@ProducerModule(includes = {",
        "    BadNonPublicModule.class, OtherPublicModule.class, OkNonPublicModule.class",
        "})",
        "public final class PublicModule {",
        "}");
    JavaFileObject badNonPublicModuleFile =
        JavaFileObjects.forSourceLines(
            "test.BadNonPublicModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "",
            "@ProducerModule",
            "final class BadNonPublicModule {",
            "  @Produces",
            "  int produceInt() {",
            "    return 42;",
            "  }",
            "}");
    JavaFileObject okNonPublicModuleFile = JavaFileObjects.forSourceLines("test.OkNonPublicModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class OkNonPublicModule {",
        "  @Produces",
        "  static String produceString() {",
        "    return \"foo\";",
        "  }",
        "}");
    JavaFileObject otherPublicModuleFile = JavaFileObjects.forSourceLines("test.OtherPublicModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "@ProducerModule",
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
  public void argumentNamedModuleCompiles() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces String produceString(int module) {",
        "    return null;",
        "  }",
        "}");
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).succeeded();
  }
}
