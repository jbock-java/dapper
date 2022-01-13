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

import static com.google.common.truth.Truth.assertThat;
import static dagger.internal.codegen.javapoet.TypeNames.MEMBERS_INJECTOR;
import static dagger.internal.codegen.javapoet.TypeNames.PROVIDER;
import static dagger.internal.codegen.javapoet.TypeNames.membersInjectorOf;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;

import com.google.testing.compile.CompilationExtension;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.binding.FrameworkField;
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
    assertThat(FrameworkField.create(MEMBERS_INJECTOR, xTypeName, "test").type())
        .isEqualTo(membersInjectorOf(xTypeName));
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
