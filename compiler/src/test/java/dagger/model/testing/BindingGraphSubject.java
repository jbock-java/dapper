/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.model.testing;

import static dagger.internal.codegen.base.Util.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static io.jbock.common.truth.Truth.assertAbout;
import static java.util.Objects.requireNonNull;

import dagger.spi.model.Binding;
import dagger.spi.model.BindingGraph;
import io.jbock.common.truth.FailureMetadata;
import io.jbock.common.truth.Subject;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/** A Truth subject for making assertions on a {@link BindingGraph}. */
public final class BindingGraphSubject extends Subject {

  /** Starts a fluent assertion about a {@link BindingGraph}. */
  public static BindingGraphSubject assertThat(BindingGraph bindingGraph) {
    return assertAbout(BindingGraphSubject::new).that(bindingGraph);
  }

  private final BindingGraph actual;

  private BindingGraphSubject(FailureMetadata metadata, BindingGraph actual) {
    super(metadata, actual);
    this.actual = requireNonNull(actual);
  }

  /**
   * Asserts that the graph has at least one binding with an unqualified key.
   *
   * @param type the canonical name of the type, as returned by {@link TypeMirror#toString()}
   */
  public void hasBindingWithKey(String type) {
    bindingWithKey(type);
  }

  /**
   * Asserts that the graph has at least one binding with a qualified key.
   *
   * @param qualifier the canonical string form of the qualifier, as returned by {@link
   *     javax.lang.model.element.AnnotationMirror AnnotationMirror.toString()}
   * @param type the canonical name of the type, as returned by {@link TypeMirror#toString()}
   */
  public void hasBindingWithKey(String qualifier, String type) {
    bindingWithKey(qualifier, type);
  }

  /**
   * Returns a subject for testing the binding for an unqualified key.
   *
   * @param type the canonical name of the type, as returned by {@link TypeMirror#toString()}
   */
  public BindingSubject bindingWithKey(String type) {
    return bindingWithKeyString(keyString(type));
  }

  /**
   * Returns a subject for testing the binding for a qualified key.
   *
   * @param qualifier the canonical string form of the qualifier, as returned by {@link
   *     javax.lang.model.element.AnnotationMirror AnnotationMirror.toString()}
   * @param type the canonical name of the type, as returned by {@link TypeMirror#toString()}
   */
  public BindingSubject bindingWithKey(String qualifier, String type) {
    return bindingWithKeyString(keyString(qualifier, type));
  }

  private BindingSubject bindingWithKeyString(String keyString) {
    Set<Binding> bindings = getBindingNodes(keyString);
    // TODO(dpb): Handle multiple bindings for the same key.
    check("bindingsWithKey(%s)", keyString).that(bindings).hasSize(1);
    return check("bindingWithKey(%s)", keyString)
        .about(BindingSubject::new)
        .that(getOnlyElement(bindings));
  }

  private Set<Binding> getBindingNodes(String keyString) {
    return actual.bindings().stream()
        .filter(binding -> binding.key().toString().equals(keyString))
        .collect(toImmutableSet());
  }

  private static String keyString(String type) {
    return type;
  }

  private static String keyString(String qualifier, String type) {
    return String.format("%s %s", qualifier, type);
  }

  /** A Truth subject for a {@link Binding}. */
  public final class BindingSubject extends Subject {

    private final Binding actual;

    BindingSubject(FailureMetadata metadata, Binding actual) {
      super(metadata, actual);
      this.actual = actual;
    }

    /**
     * Asserts that the binding depends on a binding with an unqualified key.
     *
     * @param type the canonical name of the type, as returned by {@link TypeMirror#toString()}
     */
    public void dependsOnBindingWithKey(String type) {
      dependsOnBindingWithKeyString(keyString(type));
    }

    /**
     * Asserts that the binding depends on a binding with a qualified key.
     *
     * @param qualifier the canonical string form of the qualifier, as returned by {@link
     *     javax.lang.model.element.AnnotationMirror AnnotationMirror.toString()}
     * @param type the canonical name of the type, as returned by {@link TypeMirror#toString()}
     */
    public void dependsOnBindingWithKey(String qualifier, String type) {
      dependsOnBindingWithKeyString(keyString(qualifier, type));
    }

    private void dependsOnBindingWithKeyString(String keyString) {
      if (actualBindingGraph().requestedBindings(actual).stream()
          .noneMatch(binding -> binding.key().toString().equals(keyString))) {
        failWithActual("expected to depend on binding with key", keyString);
      }
    }

    private BindingGraph actualBindingGraph() {
      return BindingGraphSubject.this.actual;
    }
  }
}
