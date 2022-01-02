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

import static dagger.internal.codegen.binding.SourceFiles.protectAgainstKeywords;

import com.google.auto.common.MoreTypes;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import java.util.Iterator;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleTypeVisitor9;

/**
 * Suggests a variable name for a type based on a {@link Key}. Prefer {@link
 * DependencyVariableNamer} for cases where a specific {@link DependencyRequest} is present.
 */
public final class KeyVariableNamer {
  /** Simple names that are very common. Inspired by https://errorprone.info/bugpattern/BadImport */
  private static final Set<String> VERY_SIMPLE_NAMES =
      Set.of(
          "Builder",
          "Factory",
          "Component",
          "Subcomponent",
          "Injector");

  private static final TypeVisitor<Void, StringBuilder> TYPE_NAMER =
      new SimpleTypeVisitor9<>() {
        @Override
        public Void visitDeclared(DeclaredType declaredType, StringBuilder builder) {
          TypeElement element = MoreTypes.asTypeElement(declaredType);
          if (element.getNestingKind().isNested()
              && VERY_SIMPLE_NAMES.contains(element.getSimpleName().toString())) {
            builder.append(element.getEnclosingElement().getSimpleName());
          }

          builder.append(element.getSimpleName());
          Iterator<? extends TypeMirror> argumentIterator =
              declaredType.getTypeArguments().iterator();
          if (argumentIterator.hasNext()) {
            builder.append("Of");
            TypeMirror first = argumentIterator.next();
            first.accept(this, builder);
            while (argumentIterator.hasNext()) {
              builder.append("And");
              argumentIterator.next().accept(this, builder);
            }
          }
          return null;
        }

        @Override
        public Void visitPrimitive(PrimitiveType type, StringBuilder builder) {
          String str = type.toString();
          builder.append(Character.toUpperCase(str.charAt(0))).append(str.substring(1));
          return null;
        }

        @Override
        public Void visitArray(ArrayType type, StringBuilder builder) {
          type.getComponentType().accept(this, builder);
          builder.append("Array");
          return null;
        }
      };

  private KeyVariableNamer() {
  }

  public static String name(Key key) {
    if (key.multibindingContributionIdentifier().isPresent()) {
      @SuppressWarnings("deprecation")
      String bindingElement = key.multibindingContributionIdentifier().get().bindingElement();
      return bindingElement;
    }

    StringBuilder builder = new StringBuilder();

    if (key.qualifier().isPresent()) {
      // TODO(gak): Use a better name for fields with qualifiers with members.
      builder.append(key.qualifier().get().getAnnotationType().asElement().getSimpleName());
    }

    key.type().accept(TYPE_NAMER, builder);

    return protectAgainstKeywords(Character.toLowerCase(builder.charAt(0)) + builder.substring(1));
  }
}
