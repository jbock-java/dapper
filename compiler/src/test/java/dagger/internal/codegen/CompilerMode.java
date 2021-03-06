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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The configuration options for compiler modes. */
enum CompilerMode {
  DEFAULT_MODE,
  FAST_INIT_MODE("-Adagger.fastInit=enabled");

  private final List<String> javacopts;

  CompilerMode(String... javacopts) {
    this.javacopts = Arrays.asList(javacopts);
  }

  /** Returns the javacopts for this compiler mode. */
  List<String> javacopts() {
    return Stream.concat(javacopts.stream(), Stream.of("-Adagger.generatedClassExtendsComponent=enabled"))
        .collect(Collectors.toList());
  }

  /** Returns the javacopts for this compiler mode. */
  List<String> javacopts(boolean generatedClassExtends) {
    return generatedClassExtends ? javacopts() : javacopts;
  }

  /**
   * Returns a {@link JavaFileBuilder} that builds {@link javax.tools.JavaFileObject}s for this
   * mode.
   */
  JavaFileBuilder javaFileBuilder(String qualifiedName) {
    return new JavaFileBuilder(this, qualifiedName);
  }
}
