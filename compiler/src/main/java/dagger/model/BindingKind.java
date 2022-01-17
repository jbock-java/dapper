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

package dagger.model;

/** Represents the different kinds of {@link Binding}s that can exist in a binding graph. */
public enum BindingKind {
  /** A binding for an {@link jakarta.inject.Inject}-annotated constructor. */
  INJECTION,

  /** A binding for a {@link dagger.Provides}-annotated method. */
  PROVISION,

  /**
   * A binding for an {@link jakarta.inject.Inject}-annotated constructor that contains at least one
   * {@link dagger.assisted.Assisted}-annotated parameter.
   */
  ASSISTED_INJECTION,

  /** A binding for an {@link dagger.assisted.AssistedFactory}-annotated type. */
  ASSISTED_FACTORY,

  /**
   * An implicit binding for a {@link dagger.Component}-annotated type.
   */
  COMPONENT,

  /**
   * A binding for a provision method on a component's {@linkplain dagger.Component#dependencies()
   * dependency}.
   */
  COMPONENT_PROVISION,

  /**
   * A binding for an instance of a component's {@linkplain dagger.Component#dependencies()
   * dependency}.
   */
  COMPONENT_DEPENDENCY,

  /**
   * A binding for a subcomponent creator (a {@linkplain dagger.Subcomponent.Builder builder} or
   * {@linkplain dagger.Subcomponent.Factory factory}).
   *
   * @since 2.22 (previously named {@code SUBCOMPONENT_BUILDER})
   */
  SUBCOMPONENT_CREATOR,

  /** A binding for a {@link dagger.BindsInstance}-annotated builder method. */
  BOUND_INSTANCE,

  /**
   * A binding for {@link dagger.Binds}-annotated method that that delegates from requests for one
   * key to another.
   */
  // TODO(dpb,ronshapiro): This name is confusing and could use work. Not all usages of @Binds
  // bindings are simple delegations and we should have a name that better reflects that
  DELEGATE,
}
