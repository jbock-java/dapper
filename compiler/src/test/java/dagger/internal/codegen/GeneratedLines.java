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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Common lines outputted during code generation.
 */
public final class GeneratedLines {
  private static final String DAGGER_GENERATED_ANNOTATION = "@DaggerGenerated";

  private static final String GENERATED_ANNOTATION_0 =
      "@Generated(";

  private static final String GENERATED_ANNOTATION_1 =
      "    value = \"dagger.internal.codegen.ComponentProcessor\",";

  private static final String GENERATED_ANNOTATION_2 =
      "    comments = \"https://github.com/jbock-java/dapper\"";

  private static final String GENERATED_ANNOTATION_3 =
      ")";

  private static final String SUPPRESS_WARNINGS_ANNOTATION_0 =
      "@SuppressWarnings({";
  private static final String SUPPRESS_WARNINGS_ANNOTATION_1 =
      "    \"unchecked\",";
  private static final String SUPPRESS_WARNINGS_ANNOTATION_2 =
      "    \"rawtypes\"";
  private static final String SUPPRESS_WARNINGS_ANNOTATION_3 =
      "})";

  private static final String GENERATED_ANNOTATION = String.join("", List.of(
      GENERATED_ANNOTATION_0,
      GENERATED_ANNOTATION_1,
      GENERATED_ANNOTATION_2,
      GENERATED_ANNOTATION_3));

  private static final String SUPPRESS_WARNINGS_ANNOTATION = String.join("", List.of(
      SUPPRESS_WARNINGS_ANNOTATION_0,
      SUPPRESS_WARNINGS_ANNOTATION_1,
      SUPPRESS_WARNINGS_ANNOTATION_2,
      SUPPRESS_WARNINGS_ANNOTATION_3));

  private static final String IMPORT_DAGGER_GENERATED = "import dagger.internal.DaggerGenerated;";

  private static final String IMPORT_GENERATED_ANNOTATION =
      "import javax.annotation.processing.Generated;";

  /** Returns a {@code String} of sorted imports. Includes generated imports automatically. */
  public static String generatedImports(String... extraImports) {
    return String.join("\n", generatedImportsIndividual(extraImports));
  }

  /** Returns a {@code String} of sorted imports. Includes generated imports automatically. */
  public static String[] generatedImportsIndividual(String... extraImports) {
    return ImmutableSet.<String>builder()
        .add(IMPORT_DAGGER_GENERATED)
        .add(IMPORT_GENERATED_ANNOTATION)
        .add(extraImports)
        .build()
        .stream()
        .sorted()
        .toArray(String[]::new);
  }

  /** Returns the annotations for a generated class. */
  public static String generatedAnnotations() {
    return Joiner.on('\n')
        .join(DAGGER_GENERATED_ANNOTATION, GENERATED_ANNOTATION, SUPPRESS_WARNINGS_ANNOTATION);
  }

  public static String[] generatedAnnotationsIndividual() {
    return new String[]{
        DAGGER_GENERATED_ANNOTATION,
        GENERATED_ANNOTATION_0,
        GENERATED_ANNOTATION_1,
        GENERATED_ANNOTATION_2,
        GENERATED_ANNOTATION_3,
        SUPPRESS_WARNINGS_ANNOTATION_0,
        SUPPRESS_WARNINGS_ANNOTATION_1,
        SUPPRESS_WARNINGS_ANNOTATION_2,
        SUPPRESS_WARNINGS_ANNOTATION_3};
  }

  /** Returns the annotations for a generated class without {@code SuppressWarnings}. */
  public static String generatedAnnotationsWithoutSuppressWarnings() {
    return Joiner.on('\n').join(DAGGER_GENERATED_ANNOTATION, GENERATED_ANNOTATION);
  }
}
