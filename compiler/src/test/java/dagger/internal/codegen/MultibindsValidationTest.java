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

import jakarta.inject.Qualifier;
import org.junit.jupiter.api.Test;

class MultibindsValidationTest {

  private final String moduleDeclaration = "@Module abstract class %s { %s }";

  @Test
  void notWithinModule() {
    assertThatMethodInUnannotatedClass("@Multibinds abstract Set<Object> emptySet();")
        .hasError("@Multibinds methods can only be present within a @Module");
  }

  @Test
  void voidMethod() {
    assertThatModuleMethod("@Multibinds abstract void voidMethod();")
        .withDeclaration(moduleDeclaration)
        .hasError("@Multibinds methods must return Map<K, V> or Set<T>");
  }

  @Test
  void primitiveMethod() {
    assertThatModuleMethod("@Multibinds abstract int primitive();")
        .withDeclaration(moduleDeclaration)
        .hasError("@Multibinds methods must return Map<K, V> or Set<T>");
  }

  @Test
  void rawMap() {
    assertThatModuleMethod("@Multibinds abstract Map rawMap();")
        .withDeclaration(moduleDeclaration)
        .hasError("@Multibinds methods must return Map<K, V> or Set<T>");
  }

  @Test
  void wildcardMap() {
    assertThatModuleMethod("@Multibinds abstract Map<?, ?> wildcardMap();")
        .withDeclaration(moduleDeclaration)
        .hasError("@Multibinds methods must return Map<K, V> or Set<T>");
  }

  @Test
  void providerMap() {
    assertThatModuleMethod("@Multibinds abstract Map<String, Provider<Object>> providerMap();")
        .withDeclaration(moduleDeclaration)
        .hasError("@Multibinds methods must return Map<K, V> or Set<T>");
  }

  @Test
  void rawSet() {
    assertThatModuleMethod("@Multibinds abstract Set rawSet();")
        .withDeclaration(moduleDeclaration)
        .hasError("@Multibinds methods must return Map<K, V> or Set<T>");
  }

  @Test
  void wildcardSet() {
    assertThatModuleMethod("@Multibinds abstract Set<?> wildcardSet();")
        .withDeclaration(moduleDeclaration)
        .hasError("@Multibinds methods must return Map<K, V> or Set<T>");
  }

  @Test
  void providerSet() {
    assertThatModuleMethod("@Multibinds abstract Set<Provider<Object>> providerSet();")
        .withDeclaration(moduleDeclaration)
        .hasError("@Multibinds methods must return Map<K, V> or Set<T>");
  }

  @Test
  void overqualifiedSet() {
    assertThatModuleMethod(
        "@Multibinds @SomeQualifier @OtherQualifier "
            + "abstract Set<Object> tooManyQualifiersSet();")
        .withDeclaration(moduleDeclaration)
        .importing(SomeQualifier.class, OtherQualifier.class)
        .hasError("may not use more than one @Qualifier");
  }

  @Test
  void overqualifiedMap() {
    assertThatModuleMethod(
        "@Multibinds @SomeQualifier @OtherQualifier "
            + "abstract Map<String, Object> tooManyQualifiersMap();")
        .withDeclaration(moduleDeclaration)
        .importing(SomeQualifier.class, OtherQualifier.class)
        .hasError("may not use more than one @Qualifier");
  }

  @Test
  void hasParameters() {
    assertThatModuleMethod("@Multibinds abstract Set<String> parameters(Object param);")
        .hasError("@Multibinds methods cannot have parameters");
  }

  @Qualifier
  public @interface SomeQualifier {
  }

  @Qualifier
  public @interface OtherQualifier {
  }
}
