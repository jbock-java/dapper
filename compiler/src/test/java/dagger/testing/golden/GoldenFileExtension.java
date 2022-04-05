/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.testing.golden;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.lang.reflect.Method;


/** A test rule that manages golden files for tests. */
public final class GoldenFileExtension implements BeforeEachCallback, BeforeAllCallback, ParameterResolver {

  private Class<?> testClass;
  private Method testMethod;

  @Override
  public void beforeEach(ExtensionContext context) {
    testMethod = context.getRequiredTestMethod();
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    testClass = context.getRequiredTestClass();
  }

  @Override
  public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
    Class<?> parameterType = parameterContext.getParameter().getType();
    return parameterType.equals(GoldenFile.class);
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
    return new GoldenFile(testClass, testMethod);
  }
}
