/*
 * Copyright (C) 2022 The Dagger Authors.
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
import static io.jbock.testing.compile.CompilationSubject.assertThat;

import dagger.testing.golden.GoldenFile;
import dagger.testing.golden.GoldenFileExtension;
import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@ExtendWith(GoldenFileExtension.class)
final class ComponentProtectedTypeTest {

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void componentAccessesProtectedType_succeeds(CompilerMode compilerMode, GoldenFile goldenFile) {
    JavaFileObject baseSrc =
        JavaFileObjects.forSourceLines(
            "test.sub.TestComponentBase",
            "package test.sub;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Singleton;",
            "",
            "public abstract class TestComponentBase {",
            "  static class Dep {",
            "    @Inject",
            "    Dep() {}",
            "  }",
            "",
            "  @Singleton",
            "  protected static final class ProtectedType {",
            "    @Inject",
            "    ProtectedType(Dep dep) {}",
            "  }",
            "}");
    JavaFileObject componentSrc =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "import jakarta.inject.Singleton;",
            "import test.sub.TestComponentBase;",
            "",
            "@Singleton",
            "@Component",
            "public abstract class TestComponent extends TestComponentBase {",
            // This component method will be implemented as:
            // TestComponentBase.ProtectedType provideProtectedType() {
            //   return protectedTypeProvider.get();
            // }
            // The protectedTypeProvider can't be a raw provider, otherwise it will have a type cast
            // error. So protected accessibility should be evaluated when checking accessibility of
            // a type.
            "  abstract TestComponentBase.ProtectedType provideProtectedType();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts(false)).compile(baseSrc, componentSrc);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(goldenFile.get("test.DaggerTestComponent", compilerMode));
  }
}
