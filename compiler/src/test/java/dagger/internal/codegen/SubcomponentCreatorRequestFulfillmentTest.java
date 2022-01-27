/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.SUBCOMPONENT_BUILDER;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.SUBCOMPONENT_FACTORY;
import static io.jbock.testing.compile.CompilationSubject.assertThat;

import io.jbock.testing.compile.Compilation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SubcomponentCreatorRequestFulfillmentTest {

  private static List<ComponentCreatorTestData> dataSource() {
    List<ComponentCreatorTestData> result = new ArrayList<>();
    result.add(new ComponentCreatorTestData(DEFAULT_MODE, SUBCOMPONENT_FACTORY));
    result.add(new ComponentCreatorTestData(DEFAULT_MODE, SUBCOMPONENT_BUILDER));
    result.add(new ComponentCreatorTestData(FAST_INIT_MODE, SUBCOMPONENT_FACTORY));
    result.add(new ComponentCreatorTestData(FAST_INIT_MODE, SUBCOMPONENT_BUILDER));
    return result;
  }

  @MethodSource("dataSource")
  @ParameterizedTest
  void testInlinedSubcomponentCreators_componentMethod(ComponentCreatorTestData data) {
    JavaFileObject subcomponent =
        data.preprocessedJavaFile(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Sub {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Sub build();",
            "  }",
            "}");
    JavaFileObject usesSubcomponent =
        data.preprocessedJavaFile(
            "test.UsesSubcomponent",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "class UsesSubcomponent {",
            "  @Inject UsesSubcomponent(Sub.Builder subBuilder) {}",
            "}");
    JavaFileObject component =
        data.preprocessedJavaFile(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface C {",
            "  Sub.Builder sBuilder();",
            "  UsesSubcomponent usesSubcomponent();",
            "}");

    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedImports());
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotations());
    Collections.addAll(generatedComponent,
        "final class DaggerC implements C {",
        "  @Override",
        "  public Sub.Builder sBuilder() {",
        "    return new SubBuilder(c);",
        "  }",
        "",
        "  @Override",
        "  public UsesSubcomponent usesSubcomponent() {",
        "    return new UsesSubcomponent(new SubBuilder(c));",
        "  }",
        "",
        "  private static final class SubBuilder implements Sub.Builder {",
        "    @Override",
        "    public Sub build() {",
        "      return new SubImpl(c);",
        "    }",
        "  }",
        "",
        "  private static final class SubImpl implements Sub {",
        "    private SubImpl(DaggerC c) {",
        "      this.c = c;",
        "    }",
        "  }",
        "}");

    Compilation compilation = data.compile(subcomponent, usesSubcomponent, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerC")
        .containsLines(data.processLines(generatedComponent));
  }
}
