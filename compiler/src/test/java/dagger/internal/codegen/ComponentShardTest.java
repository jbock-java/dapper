/*
 * Copyright (C) 2020 The Dagger Authors.
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
import io.jbock.testing.compile.Compiler;
import io.jbock.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@ExtendWith(GoldenFileExtension.class)
class ComponentShardTest {
  private static final int BINDINGS_PER_SHARD = 2;

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void testNewShardCreated(CompilerMode compilerMode, GoldenFile goldenFile) {
    // Add all bindings.
    //
    //     1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7
    //          ^--------/
    //
    List<JavaFileObject> javaFileObjects = new ArrayList<>();
        // Shard 2: Bindings (1)
    javaFileObjects.add(createBinding("Binding1", "Binding2 binding2"));
        // Shard 1: Bindings (2, 3, 4, 5). Contains more than 2 bindings due to cycle.
    javaFileObjects.add(createBinding("Binding2", "Binding3 binding3"));
    javaFileObjects.add(createBinding("Binding3", "Binding4 binding4"));
    javaFileObjects.add(createBinding("Binding4", "Binding5 binding5, Provider<Binding2> binding2Provider"));
    javaFileObjects.add(createBinding("Binding5", "Binding6 binding6"));
    // Component shard: Bindings (6, 7)
    javaFileObjects.add(createBinding("Binding6", "Binding7 binding7"));
    javaFileObjects.add(createBinding("Binding7"));
    
    // Add the component with entry points for each binding and its provider.
    javaFileObjects.add(
        JavaFileObjects.forSourceLines(
            "dagger.internal.codegen.TestComponent",
            "package dagger.internal.codegen;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface TestComponent {",
            "  Binding1 binding1();",
            "  Binding2 binding2();",
            "  Binding3 binding3();",
            "  Binding4 binding4();",
            "  Binding5 binding5();",
            "  Binding6 binding6();",
            "  Binding7 binding7();",
            "  Provider<Binding1> providerBinding1();",
            "  Provider<Binding2> providerBinding2();",
            "  Provider<Binding3> providerBinding3();",
            "  Provider<Binding4> providerBinding4();",
            "  Provider<Binding5> providerBinding5();",
            "  Provider<Binding6> providerBinding6();",
            "  Provider<Binding7> providerBinding7();",
            "}"));

    Compilation compilation = compiler(compilerMode).compile(javaFileObjects);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("dagger.internal.codegen.DaggerTestComponent")
        .containsLines(
            goldenFile.get("dagger.internal.codegen.DaggerTestComponent", compilerMode));
  }

  private static JavaFileObject createBinding(String bindingName, String... deps) {
    return JavaFileObjects.forSourceLines(
        "dagger.internal.codegen." + bindingName,
        "package dagger.internal.codegen;",
        "",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Provider;",
        "import jakarta.inject.Singleton;",
        "",
        "@Singleton",
        "final class " + bindingName + " {",
        "  @Inject",
        "  " + bindingName + "(" + String.join(", ", deps) + ") {}",
        "}");
  }

  private Compiler compiler(CompilerMode compilerMode) {
    Set<String> options = new LinkedHashSet<>();
    options.add("-Adagger.generatedClassExtendsComponent=DISABLED");
    options.add("-Adagger.keysPerComponentShard=" + BINDINGS_PER_SHARD);
    options.addAll(compilerMode.javacopts(false));
    return compilerWithOptions(options);
  }
}
