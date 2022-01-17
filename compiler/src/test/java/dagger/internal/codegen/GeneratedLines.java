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

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Common lines outputted during code generation.
 */
public final class GeneratedLines {

  /** Returns a {@code String} of sorted imports. Includes generated imports automatically. */
  public static String[] generatedImports(String... extraImports) {
    Set<String> result = new TreeSet<>();
    result.add("import javax.annotation.processing.Generated;");
    Collections.addAll(result, extraImports);
    return result.toArray(String[]::new);
  }

  public static String[] generatedAnnotations() {
    return new String[]{
        "@Generated(",
        "    value = \"dagger.internal.codegen.ComponentProcessor\",",
        "    comments = \"https://github.com/jbock-java/dapper\"",
        ")",
        "@SuppressWarnings({",
        "    \"unchecked\",",
        "    \"rawtypes\"",
        "})"};
  }
}
