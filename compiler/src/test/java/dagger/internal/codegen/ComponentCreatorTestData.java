/*
 * Copyright (C) 2018 The Dagger Authors.
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
import static dagger.internal.codegen.base.ComponentCreatorKind.FACTORY;
import static dagger.internal.codegen.binding.ErrorMessages.creatorMessagesFor;

import dagger.internal.codegen.base.ComponentCreatorAnnotation;
import dagger.internal.codegen.base.ComponentCreatorKind;
import dagger.internal.codegen.binding.ErrorMessages;
import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;

final class ComponentCreatorTestData {

  final CompilerMode compilerMode;
  final ComponentCreatorKind creatorKind;

  final ErrorMessages.ComponentCreatorMessages messages;

  ComponentCreatorTestData(
      CompilerMode compilerMode, ComponentCreatorAnnotation componentCreatorAnnotation) {
    this.compilerMode = compilerMode;
    this.creatorKind = componentCreatorAnnotation.creatorKind();
    this.messages = creatorMessagesFor(componentCreatorAnnotation);
  }

  // For tests where code for both builders and factories can be largely equivalent, i.e. when there
  // is nothing to set, just preprocess the lines to change code written for a builder to code for a
  // factory.
  // For more complicated code, use a JavaFileBuilder to add different code depending on the creator
  // kind.

  /**
   * Processes the given lines, replacing builder-related names with factory-related names if the
   * creator kind is {@code FACTORY}.
   */
  String process(String... lines) {
    return String.join("\n", processLines(Arrays.asList(lines)));
  }

  List<String> processLines(List<String> lines) {
    Stream<String> stream = lines.stream();
    if (creatorKind.equals(FACTORY)) {
      stream =
          stream.map(
              line ->
                  line.replace("Builder", "Factory")
                      .replace("builder", "factory")
                      .replace("build", "createComponent"));
    }
    return stream.collect(Collectors.toList());
  }

  /**
   * Returns a Java file with the {@linkplain #process(String...)} processed} versions of the given
   * lines.
   */
  JavaFileObject preprocessedJavaFile(String fullyQualifiedName, String... lines) {
    return JavaFileObjects.forSourceString(fullyQualifiedName, process(lines));
  }

  /** Returns a file builder for the current creator kind. */
  JavaFileBuilder javaFileBuilder(String qualifiedName) {
    return new JavaFileBuilder(qualifiedName).withSettings(compilerMode, creatorKind);
  }

  /** Compiles the given files with the set compiler mode's javacopts. */
  Compilation compile(JavaFileObject... files) {
    return compilerWithOptions(compilerMode.javacopts()).compile(files);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", "[", "]")
        .add(compilerMode.toString())
        .add(creatorKind.toString())
        .toString();
  }
}
