/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.internal.codegen.bindinggraphvalidation;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

public class SetMultibindingValidationTest {
  private static final JavaFileObject FOO =
      JavaFileObjects.forSourceLines(
          "test.Foo",
          "package test;",
          "",
          "public interface Foo {}");

  @Test
  public void testMultipleSetBindingsViaElementsIntoSet() {
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Binds;",
        "import dagger.Provides;",
        "import dagger.multibindings.ElementsIntoSet;",
        "import java.util.HashSet;",
        "import java.util.Set;",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Qualifier;",
        "",
        "@dagger.Module",
        "interface TestModule {",
        "",
        "  @Qualifier",
        "  @interface Internal {}",
        "",
        "  @Provides @Internal static Set<Foo> provideSet() { return new HashSet<>(); }",
        "",
        "  @Binds @ElementsIntoSet Set<Foo> bindSet(@Internal Set<Foo> fooSet);",
        "",
        "  @Binds @ElementsIntoSet Set<Foo> bindSetAgain(@Internal Set<Foo> fooSet);",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Set;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  Set<Foo> setOfFoo();",
        "}");
    Compilation compilation = daggerCompiler().compile(FOO, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Multiple set contributions into Set<Foo> for the same contribution key: "
                + "@TestModule.Internal Set<Foo>");
  }
}
