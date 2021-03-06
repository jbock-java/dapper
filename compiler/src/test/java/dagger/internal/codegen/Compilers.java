/*
 * Copyright (C) 2016 The Dagger Authors.
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

import dagger.internal.codegen.base.Util;
import io.jbock.testing.compile.Compiler;

import javax.annotation.processing.Processor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.jbock.testing.compile.Compiler.javac;

/** {@link Compiler} instances for testing Dagger. */
public final class Compilers {

  static final List<String> DEFAULT_JAVACOPTS =
      List.of("-Adagger.experimentalDaggerErrorMessages=enabled");

  static final List<String> JAVACOPTS_GENERATED_CLASS_EXTENDS_COMPONENT =
      List.of(
          "-Adagger.generatedClassExtendsComponent=enabled",
          "-Adagger.experimentalDaggerErrorMessages=enabled");

  /**
   * Returns a compiler that runs the Dagger and {@code @AutoAnnotation} processors, along with
   * extras.
   */
  public static Compiler daggerCompiler(Processor... extraProcessors) {
    List<Processor> processors = new ArrayList<>();
    processors.add(new ComponentProcessor());
    Collections.addAll(processors, extraProcessors);
    return javac().withProcessors(processors).withOptions(JAVACOPTS_GENERATED_CLASS_EXTENDS_COMPONENT);
  }

  public static Compiler compilerWithOptions(CompilerMode... compilerModes) {
    List<String> options = new ArrayList<>();
    for (CompilerMode compilerMode : compilerModes) {
      options.addAll(compilerMode.javacopts());
    }
    return compilerWithOptions(options);
  }

  public static Compiler compilerWithOptions(String... options) {
    return compilerWithOptions(Arrays.asList(options));
  }

  public static Compiler compilerWithOptions(Iterable<String> options) {
    return daggerCompiler().withOptions(Util.concat(DEFAULT_JAVACOPTS, Util.listOf(options)));
  }

  private Compilers() {
  }
}
