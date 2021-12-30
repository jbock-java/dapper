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

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.TestUtils.message;

public class MapMultibindingValidationTest {

  @Test
  public void duplicateMapKeys_WrappedMapKey() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.MapModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.MapKey;",
            "",
            "@Module",
            "abstract class MapModule {",
            "",
            "  @MapKey(unwrapValue = false)",
            "  @interface WrappedMapKey {",
            "    String value();",
            "  }",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @WrappedMapKey(\"foo\")",
            "  static String stringMapEntry1() { return \"\"; }",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @WrappedMapKey(\"foo\")",
            "  static String stringMapEntry2() { return \"\"; }",
            "}");

    JavaFileObject component = component("Map<test.MapModule.WrappedMapKey, String> objects();");

    Compilation compilation = daggerCompiler().compile(component, module);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "\033[1;31m[Dagger/MapKeys]\033[0m The same map key is bound more than once for "
                    + "Map<MapModule.WrappedMapKey,String>",
                "    @Provides @IntoMap @MapModule.WrappedMapKey(\"foo\") String "
                    + "MapModule.stringMapEntry1()",
                "    @Provides @IntoMap @MapModule.WrappedMapKey(\"foo\") String "
                    + "MapModule.stringMapEntry2()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  private static JavaFileObject component(String... entryPoints) {
    return JavaFileObjects.forSourceLines(
        "test.TestComponent",
        ImmutableList.<String>builder()
            .add(
                "package test;",
                "",
                "import dagger.Component;",
                "import dagger.producers.Producer;",
                "import java.util.Map;",
                "import jakarta.inject.Provider;",
                "",
                "@Component(modules = {MapModule.class})",
                "interface TestComponent {")
            .add(entryPoints)
            .add("}")
            .build());
  }
}
