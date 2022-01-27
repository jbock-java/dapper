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

import static dagger.internal.codegen.javapoet.TypeNames.PROVIDER;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;
import static io.jbock.common.truth.Truth.assertThat;

import dagger.internal.codegen.binding.FrameworkField;
import io.jbock.javapoet.ClassName;
import io.jbock.testing.compile.CompilationExtension;
import jakarta.inject.Inject;
import javax.lang.model.util.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link FrameworkField}.
 */
@ExtendWith(CompilationExtension.class)
public class FrameworkFieldTest {

  private ClassName xTypeName;

  @BeforeEach
  public void setUp(Elements elements) {
    xTypeName =
        ClassName.get(elements.getTypeElement(X.class.getCanonicalName()));
  }

  @Test
  public void frameworkType() {
    assertThat(FrameworkField.create(PROVIDER, xTypeName, "test").type())
        .isEqualTo(providerOf(xTypeName));
  }

  @Test
  public void nameSuffix() {
    assertThat(FrameworkField.create(PROVIDER, xTypeName, "foo").name())
        .isEqualTo("fooProvider");
    assertThat(FrameworkField.create(PROVIDER, xTypeName, "fooProvider").name())
        .isEqualTo("fooProvider");
  }

  static final class X {
    @Inject
    X() {
    }
  }
}
