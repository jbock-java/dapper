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

package dagger;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Identifies annotation types that are used to associate keys with values returned by {@linkplain
 * Provides provider methods} in order to compose a {@linkplain dagger.multibindings.IntoMap map}.
 *
 * <p>Every provider method annotated with {@code @Provides} and {@code @IntoMap} must also have an
 * annotation that identifies the key for that map entry. That annotation's type must be annotated
 * with {@code @MapKey}.
 *
 * <p>Typically, the key annotation has a single member, whose value is used as the map key.
 *
 * <p>For example, to add an entry to a {@code Map<SomeEnum, Integer>} with key {@code
 * SomeEnum.FOO}, you could use an annotation called {@code @SomeEnumKey}:
 *
 * <pre><code>
 * {@literal @}MapKey
 * {@literal @}interface SomeEnumKey {
 *   SomeEnum value();
 * }
 *
 * {@literal @}Module
 * class SomeModule {
 *   {@literal @}Provides
 *   {@literal @}IntoMap
 *   {@literal @}SomeEnumKey(SomeEnum.FOO)
 *   Integer provideFooValue() {
 *     return 2;
 *   }
 * }
 *
 * class SomeInjectedType {
 *   {@literal @}Inject
 *   SomeInjectedType({@literal Map<SomeEnum, Integer>} map) {
 *     assert map.get(SomeEnum.FOO) == 2;
 *   }
 * }
 * </code></pre>
 *
 * <p>The annotation's single member can be any type except an
 * array.
 *
 * <p>See {@link dagger.multibindings} for standard unwrapped map key annotations for keys that are
 * boxed primitives, strings, or classes.
 */
@Documented
@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
public @interface MapKey {
}
