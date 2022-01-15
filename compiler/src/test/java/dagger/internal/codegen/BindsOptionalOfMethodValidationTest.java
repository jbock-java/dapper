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

import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatMethodInUnannotatedClass;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatModuleMethod;

import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

/** Tests {@code BindsOptionalOfMethodValidator}. */
class BindsOptionalOfMethodValidationTest {

  private final String moduleDeclaration;

  public BindsOptionalOfMethodValidationTest() {
    moduleDeclaration = "@Module abstract class %s { %s }";
  }

  @Test
  void nonAbstract() {
    assertThatMethod("@BindsOptionalOf Object concrete() { return null; }")
        .hasError("must be abstract");
  }

  @Test
  void hasParameters() {
    assertThatMethod("@BindsOptionalOf abstract Object hasParameters(String s1);")
        .hasError("parameters");
  }

  @Test
  void typeParameters() {
    assertThatMethod("@BindsOptionalOf abstract <S> S generic();").hasError("type parameters");
  }

  @Test
  void notInModule() {
    assertThatMethodInUnannotatedClass("@BindsOptionalOf abstract Object notInModule();")
        .hasError("within a @Module");
  }

  @Test
  void throwsException() {
    assertThatMethod("@BindsOptionalOf abstract Object throwsException() throws RuntimeException;")
        .hasError("may not throw");
  }

  @Test
  void returnsVoid() {
    assertThatMethod("@BindsOptionalOf abstract void returnsVoid();").hasError("void");
  }

  @Test
  void returnsMembersInjector() {
    assertThatMethod("@BindsOptionalOf abstract MembersInjector<Object> returnsMembersInjector();")
        .hasError("framework");
  }

  @Test
  void tooManyQualifiers() {
    assertThatMethod(
        "@BindsOptionalOf @Qualifier1 @Qualifier2 abstract String tooManyQualifiers();")
        .importing(Qualifier1.class, Qualifier2.class)
        .hasError("more than one @Qualifier");
  }

  /** An injectable value object. */
  public static final class Thing {
    @Inject
    Thing() {
    }
  }

  @Test
  void implicitlyProvidedType() {
    assertThatMethod("@BindsOptionalOf abstract Thing thing();")
        .importing(Thing.class)
        .hasError("return unqualified types that have an @Inject-annotated constructor");
  }

  @Test
  void hasScope() {
    assertThatMethod("@BindsOptionalOf @Singleton abstract String scoped();")
        .importing(Singleton.class)
        .hasError("cannot be scoped");
  }

  private DaggerModuleMethodSubject assertThatMethod(String method) {
    return assertThatModuleMethod(method).withDeclaration(moduleDeclaration);
  }

  /** A qualifier. */
  @Qualifier
  public @interface Qualifier1 {
  }

  /** A qualifier. */
  @Qualifier
  public @interface Qualifier2 {
  }
}
