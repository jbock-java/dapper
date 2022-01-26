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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.javapoet.TypeNames;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor8;

/**
 * A value object that represents a field in the generated Component class.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code Provider<String>}
 *   <li>{@code Producer<Widget>}
 *   <li>{@code Provider<Map<SomeMapKey, MapValue>>}.
 * </ul>
 */
public final class FrameworkField {

  private final ParameterizedTypeName type;
  private final String name;

  private FrameworkField(ParameterizedTypeName type, String name) {
    this.type = type;
    this.name = name;
  }

  /**
   * Creates a framework field.
   *
   * @param frameworkClassName the name of the framework class (e.g., {@code Provider})
   * @param valueTypeName the name of the type parameter of the framework class (e.g., {@code Foo}
   *     for {@code Provider<Foo>}
   * @param fieldName the name of the field
   */
  public static FrameworkField create(
      ClassName frameworkClassName, TypeName valueTypeName, String fieldName) {
    String suffix = frameworkClassName.simpleName();
    return new FrameworkField(
        ParameterizedTypeName.get(frameworkClassName, valueTypeName),
        fieldName.endsWith(suffix) ? fieldName : fieldName + suffix);
  }

  /**
   * A framework field for a {@link ContributionBinding}.
   */
  public static FrameworkField forBinding(
      ContributionBinding binding) {
    return create(
        TypeNames.PROVIDER,
        TypeName.get(binding.key().type().java()),
        frameworkFieldName(binding));
  }

  private static String frameworkFieldName(ContributionBinding binding) {
    if (binding.bindingElement().isPresent()) {
      return BINDING_ELEMENT_NAME.visit(binding.bindingElement().get(), binding);
    }
    return KeyVariableNamer.name(binding.key());
  }

  private static final ElementVisitor<String, Binding> BINDING_ELEMENT_NAME =
      new ElementKindVisitor8<>() {

        @Override
        protected String defaultAction(Element e, Binding p) {
          throw new IllegalArgumentException("Unexpected binding " + p);
        }

        @Override
        public String visitExecutableAsConstructor(ExecutableElement e, Binding p) {
          return visit(e.getEnclosingElement(), p);
        }

        @Override
        public String visitExecutableAsMethod(ExecutableElement e, Binding p) {
          return e.getSimpleName().toString();
        }

        @Override
        public String visitType(TypeElement e, Binding p) {
          String name = e.getSimpleName().toString();
          return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }

        @Override
        public String visitVariableAsParameter(VariableElement e, Binding p) {
          return e.getSimpleName().toString();
        }
      };

  public ParameterizedTypeName type() {
    return type;
  }

  public String name() {
    return name;
  }
}
