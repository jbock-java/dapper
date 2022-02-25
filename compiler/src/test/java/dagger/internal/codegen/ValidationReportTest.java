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

import static io.jbock.common.truth.Truth.assertThat;
import static io.jbock.testing.compile.CompilationSubject.assertThat;
import static io.jbock.testing.compile.Compiler.javac;

import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.validation.ValidationReport.Builder;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.testing.compile.Compilation;
import io.jbock.testing.compile.JavaFileObjects;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

public class ValidationReportTest {
  private static final JavaFileObject TEST_CLASS_FILE =
      JavaFileObjects.forSourceLines("test.TestClass",
          "package test;",
          "",
          "final class TestClass {}");

  @Test
  public void basicReport() {
    Compilation compilation =
        javac()
            .withProcessors(
                new SimpleTestProcessor() {
                  @Override
                  void test() {
                    Builder reportBuilder =
                        ValidationReport.about(getTypeElement("test.TestClass"));
                    reportBuilder.addError("simple error");
                    reportBuilder.build().printMessagesTo(processingEnv.getMessager());
                  }
                })
            .compile(TEST_CLASS_FILE);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("simple error").inFile(TEST_CLASS_FILE).onLine(3);
  }

  @Test
  public void messageOnDifferentElement() {
    Compilation compilation =
        javac()
            .withProcessors(
                new SimpleTestProcessor() {
                  @Override
                  void test() {
                    Builder reportBuilder =
                        ValidationReport.about(getTypeElement("test.TestClass"));
                    reportBuilder.addError("simple error", getTypeElement(String.class));
                    reportBuilder.build().printMessagesTo(processingEnv.getMessager());
                  }
                })
            .compile(TEST_CLASS_FILE);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("[java.lang.String] simple error")
        .inFile(TEST_CLASS_FILE)
        .onLine(3);
  }

  @Test
  public void subreport() {
    Compilation compilation =
        javac()
            .withProcessors(
                new SimpleTestProcessor() {
                  @Override
                  void test() {
                    Builder reportBuilder =
                        ValidationReport.about(getTypeElement("test.TestClass"));
                    reportBuilder.addError("simple error");
                    ValidationReport parentReport =
                        ValidationReport.about(getTypeElement(String.class))
                            .addSubreport(reportBuilder.build())
                            .build();
                    assertThat(parentReport.isClean()).isFalse();
                    parentReport.printMessagesTo(processingEnv.getMessager());
                  }
                })
            .compile(TEST_CLASS_FILE);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("simple error").inFile(TEST_CLASS_FILE).onLine(3);
  }

  private abstract static class SimpleTestProcessor extends AbstractProcessor {
    @SuppressWarnings("HidingField") // Subclasses should always use the XProcessing version.
    protected XProcessingEnv processingEnv;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of("*");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      processingEnv = XProcessingEnv.create(super.processingEnv);
      test();
      return false;
    }

    protected final XTypeElement getTypeElement(Class<?> clazz) {
      return getTypeElement(clazz.getCanonicalName());
    }

    protected final XTypeElement getTypeElement(String canonicalName) {
      return processingEnv.requireTypeElement(canonicalName);
    }

    abstract void test();
  }}
