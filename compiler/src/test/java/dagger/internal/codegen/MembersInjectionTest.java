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

import static io.jbock.common.truth.Truth.assertAbout;
import static io.jbock.testing.compile.JavaSourceSubjectFactory.javaSource;
import static io.jbock.testing.compile.JavaSourcesSubjectFactory.javaSources;

import io.jbock.testing.compile.JavaFileObjects;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class MembersInjectionTest {

  @Test
  void parentClass_injectedMembersInSupertype() {
    JavaFileObject childFile = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "public final class Child extends Parent {",
        "  @Inject Child() {}",
        "}");
    JavaFileObject parentFile = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "public abstract class Parent {",
        "  @Inject Dep dep;",
        "}");
    JavaFileObject depFile = JavaFileObjects.forSourceLines("test.Dep",
        "package test;",
        "",
        "import jakarta.inject.Inject;",
        "",
        "final class Dep {",
        "  @Inject Dep() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  Child child();",
        "}");

    assertAbout(javaSources())
        .that(List.of(childFile, parentFile, depFile, componentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Field injection has been disabled");
  }

  @Test
  void fieldInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.FieldInjection",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Provider;",
        "",
        "class FieldInjection {",
        "  @Inject String string;",
        "  @Inject Lazy<String> lazyString;",
        "  @Inject Provider<String> stringProvider;",
        "}");
    assertAbout(javaSource())
        .that(file)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Field injection has been disabled");
  }

  @Test
  void methodInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MethodInjection",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import jakarta.inject.Inject;",
        "import jakarta.inject.Provider;",
        "",
        "class MethodInjection {",
        "  @Inject void noArgs() {}",
        "  @Inject void oneArg(String string) {}",
        "  @Inject void manyArgs(",
        "      String string, Lazy<String> lazyString, Provider<String> stringProvider) {}",
        "}");

    assertAbout(javaSource())
        .that(file)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Method injection has been disabled");
  }
}
