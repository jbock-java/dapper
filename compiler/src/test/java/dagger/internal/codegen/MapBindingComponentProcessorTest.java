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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.compilerWithOptions;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MapBindingComponentProcessorTest {

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void injectMapWithoutMapBinding(CompilerMode compilerMode) {
    JavaFileObject mapModuleFile = JavaFileObjects.forSourceLines("test.MapModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.util.HashMap;",
        "import java.util.Map;",
        "",
        "@Module",
        "final class MapModule {",
        "  @Provides Map<String, String> provideAMap() {",
        "    Map<String, String> map = new HashMap<String, String>();",
        "    map.put(\"Hello\", \"World\");",
        "    return map;",
        "  }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Map;",
        "",
        "@Component(modules = {MapModule.class})",
        "interface TestComponent {",
        "  Map<String, String> dispatcher();",
        "}");
    String[] generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;")
            .addLines(GeneratedLines.generatedAnnotationsIndividual())
            .addLines(
                "final class DaggerTestComponent implements TestComponent {",
                "  private final MapModule mapModule;",
                "",
                "  @Override",
                "  public Map<String, String> dispatcher() {",
                "    return MapModule_ProvideAMapFactory.provideAMap(mapModule);",
                "  }",
                "}")
            .lines();
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(mapModuleFile, componentFile);
    assertThat(compilation).succeeded();

    assertThat(compilation).generatedSourceFile("test.DaggerTestComponent")
        .containsLines(List.of(generatedComponent));
  }
}
