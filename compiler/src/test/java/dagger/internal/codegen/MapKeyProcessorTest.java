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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// Ignore because auto-value processor pulls wrong version of auto-common
@Ignore
@RunWith(Parameterized.class)
public class MapKeyProcessorTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public MapKeyProcessorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void mapKeyCreatorFile() {
    JavaFileObject enumKeyFile = JavaFileObjects.forSourceLines("test.PathKey",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "@MapKey(unwrapValue = false)",
        "@Retention(RUNTIME)",
        "public @interface PathKey {",
        "  PathEnum value();",
        "  String relativePath() default \"Defaultpath\";",
        "}");
    JavaFileObject pathEnumFile = JavaFileObjects.forSourceLines("test.PathEnum",
        "package test;",
        "",
        "public enum PathEnum {",
        "    ADMIN,",
        "    LOGIN;",
        "}");
    List<String> generatedKeyCreator = new ArrayList<>();
    Collections.addAll(generatedKeyCreator,
        "package test;");
    Collections.addAll(generatedKeyCreator,
        GeneratedLines.generatedImportsIndividual("import com.google.auto.value.AutoAnnotation;"));
    Collections.addAll(generatedKeyCreator,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(generatedKeyCreator,
        "public final class PathKeyCreator {",
        "  private PathKeyCreator() {",
        "  }",
        "",
        "  @AutoAnnotation",
        "  public static PathKey createPathKey(PathEnum value, String relativePath) {",
        "    return new AutoAnnotation_PathKeyCreator_createPathKey(value, relativePath);",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(enumKeyFile, pathEnumFile))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.PathKeyCreator", generatedKeyCreator);
  }

  @Test
  public void nestedMapKeyCreatorFile() {
    JavaFileObject enumKeyFile = JavaFileObjects.forSourceLines("test.Container",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "public interface Container {",
        "@MapKey(unwrapValue = false)",
        "@Retention(RUNTIME)",
        "public @interface PathKey {",
        "  PathEnum value();",
        "  String relativePath() default \"Defaultpath\";",
        "}",
        "}");
    JavaFileObject pathEnumFile = JavaFileObjects.forSourceLines("test.PathEnum",
        "package test;",
        "",
        "public enum PathEnum {",
        "    ADMIN,",
        "    LOGIN;",
        "}");
    List<String> generatedKeyCreator = new ArrayList<>();
    Collections.addAll(generatedKeyCreator,
        "package test;");
    Collections.addAll(generatedKeyCreator,
        GeneratedLines.generatedImportsIndividual("import com.google.auto.value.AutoAnnotation;"));
    Collections.addAll(generatedKeyCreator,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(generatedKeyCreator,
        "public final class Container_PathKeyCreator {",
        "  private Container_PathKeyCreator() {",
        "  }",
        "",
        "  @AutoAnnotation",
        "  public static Container.PathKey createPathKey(PathEnum value, String relativePath) {",
        "    return new AutoAnnotation_Container_PathKeyCreator_createPathKey(value, relativePath);",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(enumKeyFile, pathEnumFile))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .containsLines("test.Container_PathKeyCreator", generatedKeyCreator);
  }
}
