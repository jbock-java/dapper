/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen.javapoet;

import static com.google.common.truth.Truth.assertThat;

import com.google.auto.common.MoreTypes;
import com.google.testing.compile.CompilationExtension;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CompilationExtension.class)
class ExpressionTest {

  interface Supertype {
  }

  interface Subtype extends Supertype {
  }

  @Test
  void castTo(Elements elements, Types types) {
    DaggerElements daggerElements = new DaggerElements(elements, types);
    TypeMirror subtype = type(daggerElements, Subtype.class);
    TypeMirror supertype = type(daggerElements, Supertype.class);
    Expression expression = Expression.create(subtype, "new $T() {}", subtype);

    Expression castTo = expression.castTo(supertype);

    assertThat(castTo.type()).isSameInstanceAs(supertype);
    assertThat(castTo.codeBlock().toString())
        .isEqualTo(
            "(dagger.internal.codegen.javapoet.ExpressionTest.Supertype) "
                + "new dagger.internal.codegen.javapoet.ExpressionTest.Subtype() {}");
  }

  @Test
  void box(Elements elements, Types types) {
    PrimitiveType primitiveInt = types.getPrimitiveType(TypeKind.INT);

    Expression primitiveExpression = Expression.create(primitiveInt, "5");
    DaggerElements daggerElements = new DaggerElements(elements, types);
    DaggerTypes daggerTypes = new DaggerTypes(types, daggerElements);
    Expression boxedExpression = primitiveExpression.box(daggerTypes);

    assertThat(boxedExpression.codeBlock().toString()).isEqualTo("(java.lang.Integer) 5");
    assertThat(MoreTypes.equivalence().equivalent(boxedExpression.type(), type(daggerElements, Integer.class)))
        .isTrue();
  }

  private TypeMirror type(DaggerElements elements, Class<?> clazz) {
    return elements.getTypeElement(clazz).asType();
  }
}
