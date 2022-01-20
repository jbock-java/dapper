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

package dagger.internal.codegen.base;

import java.util.Collection;
import java.util.function.Function;

/**
 * A formatter which transforms an instance of a particular type into a string
 * representation.
 *
 * @param <T> the type of the object to be transformed.
 */
public abstract class Formatter<T> implements Function<T, String> {

  public static final String INDENT = "    ";
  public static final String DOUBLE_INDENT = INDENT + INDENT;
  private static final int LIST_LIMIT = 10;

  /**
   * Performs the transformation of an object into a string representation.
   */
  public abstract String format(T object);

  /**
   * Performs the transformation of an object into a string representation in conformity with the
   * {@link Function}{@code <T, String>} contract, delegating to {@link #format(Object)}.
   */
  @Override
  public final String apply(T object) {
    return format(object);
  }

  /** Formats {@code items}, one per line. Stops after {@value #LIST_LIMIT} items. */
  public void formatIndentedList(
      StringBuilder builder, Collection<? extends T> items, int indentLevel) {
    items.stream().limit(LIST_LIMIT).forEach(item -> {
      String formatted = format(item);
      if (!formatted.isEmpty()) {
        builder.append('\n');
        appendIndent(builder, indentLevel);
        builder.append(formatted);
      }
    });
    int numberOfOtherItems = items.size() - LIST_LIMIT;
    if (numberOfOtherItems > 0) {
      builder.append('\n');
      appendIndent(builder, indentLevel);
      builder.append("and ").append(numberOfOtherItems).append(" other");
    }
    if (numberOfOtherItems > 1) {
      builder.append('s');
    }
  }

  private void appendIndent(StringBuilder builder, int indentLevel) {
    builder.append(INDENT.repeat(Math.max(0, indentLevel)));
  }

  public static String formatArgumentInList(int index, int size, CharSequence name) {
    Preconditions.checkArgument(index >= 0);
    Preconditions.checkArgument(index < size);
    StringBuilder builder = new StringBuilder();
    if (index > 0) {
      builder.append("\u2026, ");
    }
    builder.append(name);
    if (index < size - 1) {
      builder.append(", \u2026");
    }
    return builder.toString();
  }
}
