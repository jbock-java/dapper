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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.binding.SourceFiles.simpleVariableName;

import dagger.Lazy;
import dagger.spi.model.DependencyRequest;
import io.jbock.auto.common.MoreTypes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks a reasonable name for what we think is being provided from the variable name associated
 * with the {@link DependencyRequest}.  I.e. strips out words like "lazy" and "provider" if we
 * believe that those refer to {@link Lazy} and {@code Provider} rather than the type being
 * provided.
 */
//TODO(gak): develop the heuristics to get better names
final class DependencyVariableNamer {
  private static final Pattern LAZY_PROVIDER_PATTERN = Pattern.compile("lazy(\\w+)Provider");

  static String name(DependencyRequest dependency) {
    if (dependency.requestElement().isEmpty()) {
      return simpleVariableName(MoreTypes.asTypeElement(dependency.key().type().java()));
    }

    String variableName = dependency.requestElement().get().java().getSimpleName().toString();
    if (Character.isUpperCase(variableName.charAt(0))) {
      variableName = toLowerCamel(variableName);
    }
    switch (dependency.kind()) {
      case INSTANCE:
        return variableName;
      case LAZY:
        return variableName.startsWith("lazy") && !variableName.equals("lazy")
            ? toLowerCamel(variableName.substring(4))
            : variableName;
      case PROVIDER_OF_LAZY:
        Matcher matcher = LAZY_PROVIDER_PATTERN.matcher(variableName);
        if (matcher.matches()) {
          return toLowerCamel(matcher.group(1));
        }
        // fall through
      case PROVIDER:
        return variableName.endsWith("Provider") && !variableName.equals("Provider")
            ? variableName.substring(0, variableName.length() - 8)
            : variableName;
      default:
        throw new AssertionError();
    }
  }

  private static String toLowerCamel(String name) {
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }
}
