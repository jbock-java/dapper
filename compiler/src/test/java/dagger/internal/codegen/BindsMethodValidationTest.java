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
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.multibindings.IntKey;
import dagger.multibindings.LongKey;
import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import org.junit.jupiter.api.Test;

class BindsMethodValidationTest {

  private final String moduleDeclaration;

  public BindsMethodValidationTest() {
    moduleDeclaration = "@Module abstract class %s { %s }";
  }

  @Test
  void nonAbstract() {
    assertThatMethod("@Binds Object concrete(String impl) { return null; }")
        .hasError("must be abstract");
  }

  @Test
  void notAssignable() {
    assertThatMethod("@Binds abstract String notAssignable(Object impl);").hasError("assignable");
  }

  @Test
  void moreThanOneParameter() {
    assertThatMethod("@Binds abstract Object tooManyParameters(String s1, String s2);")
        .hasError("one parameter");
  }

  @Test
  void typeParameters() {
    assertThatMethod("@Binds abstract <S, T extends S> S generic(T t);")
        .hasError("type parameters");
  }

  @Test
  void notInModule() {
    assertThatMethodInUnannotatedClass("@Binds abstract Object bindObject(String s);")
        .hasError("within a @Module");
  }

  @Test
  void throwsException() {
    assertThatMethod("@Binds abstract Object throwsException(String s1) throws RuntimeException;")
        .hasError("may not throw");
  }

  @Test
  void returnsVoid() {
    assertThatMethod("@Binds abstract void returnsVoid(Object impl);").hasError("void");
  }

  @Test
  void tooManyQualifiersOnMethod() {
    assertThatMethod(
        "@Binds @Qualifier1 @Qualifier2 abstract String tooManyQualifiers(String impl);")
        .importing(Qualifier1.class, Qualifier2.class)
        .hasError("more than one @Qualifier");
  }

  @Test
  void tooManyQualifiersOnParameter() {
    assertThatMethod(
        "@Binds abstract String tooManyQualifiers(@Qualifier1 @Qualifier2 String impl);")
        .importing(Qualifier1.class, Qualifier2.class)
        .hasError("more than one @Qualifier");
  }

  @Test
  void noParameters() {
    assertThatMethod("@Binds abstract Object noParameters();").hasError("one parameter");
  }

  @Test
  void setElementsNotAssignable() {
    assertThatMethod(
        "@Binds @ElementsIntoSet abstract Set<String> bindSetOfIntegers(Set<Integer> ints);")
        .hasError("assignable");
  }

  @Test
  void setElements_primitiveArgument() {
    assertThatMethod("@Binds @ElementsIntoSet abstract Set<Number> bindInt(int integer);")
        .hasError("assignable");
  }

  @Test
  void elementsIntoSet_withRawSets() {
    assertThatMethod("@Binds @ElementsIntoSet abstract Set bindRawSet(HashSet hashSet);")
        .hasError("cannot return a raw Set");
  }

  private DaggerModuleMethodSubject assertThatMethod(String method) {
    return assertThatModuleMethod(method).withDeclaration(moduleDeclaration);
  }

  @Qualifier
  @Retention(RUNTIME)
  public @interface Qualifier1 {
  }

  @Qualifier
  @Retention(RUNTIME)
  public @interface Qualifier2 {
  }
}
