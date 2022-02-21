/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package dagger.internal.codegen.base;

import java.util.Objects;

public final class Preconditions {
  private Preconditions() {
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @throws IllegalArgumentException if {@code expression} is false
   */
  public static void checkArgument(boolean expression) {
    if (!expression) {
      throw new IllegalArgumentException();
    }
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   *     string using {@link String#valueOf(Object)}
   * @throws IllegalArgumentException if {@code expression} is false
   */
  public static void checkArgument(boolean expression, Object errorMessage) {
    if (!expression) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @since 20.0 (varargs overload since 2.0)
   */
  public static void checkArgument(
      boolean b, String errorMessageTemplate, Object p1) {
      if (!b) {
        throw new IllegalArgumentException(String.format(errorMessageTemplate, p1));
      }
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @since 20.0 (varargs overload since 2.0)
   */
  public static void checkArgument(
      boolean b, String errorMessageTemplate, Object p1, Object p2) {
    if (!b) {
      throw new IllegalArgumentException(String.format(errorMessageTemplate, p1, p2));
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @throws IllegalStateException if {@code expression} is false
   */
  public static void checkState(boolean expression) {
    if (!expression) {
      throw new IllegalStateException();
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   *     string using {@link String#valueOf(Object)}
   * @throws IllegalStateException if {@code expression} is false
   */
  public static void checkState(boolean expression, Object errorMessage) {
    if (!expression) {
      throw new IllegalStateException(String.valueOf(errorMessage));
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @since 20.0 (varargs overload since 2.0)
   */
  public static void checkState(boolean b, String errorMessageTemplate, Object p1) {
    if (!b) {
      throw new IllegalStateException(String.format(errorMessageTemplate, p1));
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @since 20.0 (varargs overload since 2.0)
   */
  public static void checkState(
      boolean b, String errorMessageTemplate, Object p1, Object p2) {
    if (!b) {
      throw new IllegalStateException(String.format(errorMessageTemplate, p1, p2));
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @since 20.0 (varargs overload since 2.0)
   */
  public static void checkState(
      boolean b,
      String errorMessageTemplate,
      Object p1,
      Object p2,
      Object p3) {
    if (!b) {
      throw new IllegalStateException(String.format(errorMessageTemplate, p1, p2, p3));
    }
  }

  public static <T> T checkNotNull(T obj) {
    return Objects.requireNonNull(obj);
  }

  public static <T> T checkNotNull(T obj, String message) {
    return Objects.requireNonNull(obj, message);
  }
}
