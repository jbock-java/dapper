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

import static dagger.internal.codegen.binding.SourceFiles.simpleVariableName;
import static io.jbock.common.truth.Truth.assertThat;

import dagger.internal.codegen.binding.SourceFiles;
import io.jbock.testing.compile.CompilationExtension;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Tests for {@link SourceFiles}. */
@ExtendWith(CompilationExtension.class)
public final class SourceFilesTest {

  private Elements elements;

  @BeforeEach
  public void setUp(Elements elements) {
    this.elements = elements;
  }


  private TypeElement typeElementFor(Class<?> clazz) {
    return elements.getTypeElement(clazz.getCanonicalName());
  }

  private static final class Int {
  }

  @Test
  public void testSimpleVariableName_typeCollisions() {
    // a handful of boxed types
    assertThat(simpleVariableName(typeElementFor(Long.class))).isEqualTo("l");
    assertThat(simpleVariableName(typeElementFor(Double.class))).isEqualTo("d");
    // not a boxed type type, but a custom type might collide
    assertThat(simpleVariableName(typeElementFor(Int.class))).isEqualTo("i");
    // void is the weird pseudo-boxed type
    assertThat(simpleVariableName(typeElementFor(Void.class))).isEqualTo("v");
    // reflective types
    assertThat(simpleVariableName(typeElementFor(Class.class))).isEqualTo("clazz");
    assertThat(simpleVariableName(typeElementFor(Package.class))).isEqualTo("pkg");
  }

  private static final class For {
  }

  private static final class Goto {
  }

  @Test
  public void testSimpleVariableName_randomKeywords() {
    assertThat(simpleVariableName(typeElementFor(For.class))).isEqualTo("for_");
    assertThat(simpleVariableName(typeElementFor(Goto.class))).isEqualTo("goto_");
  }

  @Test
  public void testSimpleVariableName() {
    assertThat(simpleVariableName(typeElementFor(Object.class))).isEqualTo("object");
    assertThat(simpleVariableName(typeElementFor(List.class))).isEqualTo("list");
  }
}
