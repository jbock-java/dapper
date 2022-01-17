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

package dagger.internal.codegen.javapoet;


import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

/** Common names and convenience methods for JavaPoet {@link TypeName} usage. */
public final class TypeNames {

  // Dagger Core classnames
  public static final ClassName ASSISTED = ClassName.get("dagger.assisted", "Assisted");
  public static final ClassName ASSISTED_FACTORY =
      ClassName.get("dagger.assisted", "AssistedFactory");
  public static final ClassName ASSISTED_INJECT =
      ClassName.get("dagger.assisted", "AssistedInject");
  public static final ClassName BINDS = ClassName.get("dagger", "Binds");
  public static final ClassName BINDS_INSTANCE = ClassName.get("dagger", "BindsInstance");
  public static final ClassName BINDS_OPTIONAL_OF = ClassName.get("dagger", "BindsOptionalOf");
  public static final ClassName COMPONENT = ClassName.get("dagger", "Component");
  public static final ClassName COMPONENT_BUILDER = COMPONENT.nestedClass("Builder");
  public static final ClassName COMPONENT_FACTORY = COMPONENT.nestedClass("Factory");
  public static final ClassName MODULE = ClassName.get("dagger", "Module");
  public static final ClassName MULTIBINDS = ClassName.get("dagger.multibindings", "Multibinds");
  public static final ClassName PROVIDES = ClassName.get("dagger", "Provides");
  public static final ClassName REUSABLE = ClassName.get("dagger", "Reusable");
  public static final ClassName SUBCOMPONENT = ClassName.get("dagger", "Subcomponent");
  public static final ClassName SUBCOMPONENT_BUILDER = SUBCOMPONENT.nestedClass("Builder");
  public static final ClassName SUBCOMPONENT_FACTORY = SUBCOMPONENT.nestedClass("Factory");

  // Dagger Internal classnames
  public static final ClassName DOUBLE_CHECK = ClassName.get("dagger.internal", "DoubleCheck");
  public static final ClassName FACTORY = ClassName.get("dagger.internal", "Factory");
  public static final ClassName INJECTED_FIELD_SIGNATURE =
      ClassName.get("dagger.internal", "InjectedFieldSignature");
  public static final ClassName INSTANCE_FACTORY =
      ClassName.get("dagger.internal", "InstanceFactory");
  public static final ClassName PROVIDER = ClassName.get("jakarta.inject", "Provider");
  public static final ClassName PROVIDER_OF_LAZY =
      ClassName.get("dagger.internal", "ProviderOfLazy");
  public static final ClassName SINGLE_CHECK = ClassName.get("dagger.internal", "SingleCheck");
  public static final ClassName LAZY = ClassName.get("dagger", "Lazy");

  // Other classnames
  public static final ClassName INJECT = ClassName.get("jakarta.inject", "Inject");
  public static final ClassName GENERATED = ClassName.get("javax.annotation.processing", "Generated");

  public static ParameterizedTypeName factoryOf(TypeName factoryType) {
    return ParameterizedTypeName.get(FACTORY, factoryType);
  }

  public static ParameterizedTypeName lazyOf(TypeName typeName) {
    return ParameterizedTypeName.get(LAZY, typeName);
  }

  public static ParameterizedTypeName providerOf(TypeName typeName) {
    return ParameterizedTypeName.get(PROVIDER, typeName);
  }

  /**
   * Returns the {@link TypeName} for the raw type of the given type name. If the argument isn't a
   * parameterized type, it returns the argument unchanged.
   */
  public static TypeName rawTypeName(TypeName typeName) {
    return (typeName instanceof ParameterizedTypeName)
        ? ((ParameterizedTypeName) typeName).rawType
        : typeName;
  }

  private TypeNames() {
  }
}
