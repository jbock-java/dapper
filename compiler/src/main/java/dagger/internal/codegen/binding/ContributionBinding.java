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

import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.base.MoreAnnotationMirrors.unwrapOptionalEquivalence;
import static java.util.Arrays.asList;

import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.internal.codegen.xprocessing.XConverters;
import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.Equivalence;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.ContributionType.HasContributionType;
import dagger.spi.model.BindingKind;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Key;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeMirror;

/**
 * An abstract class for a value object representing the mechanism by which a {@link Key} can be
 * contributed to a dependency graph.
 */
public abstract class ContributionBinding extends Binding implements HasContributionType {

  /** Returns the type that specifies this' nullability, absent if not nullable. */
  public abstract Optional<XType> nullableType();

  public abstract Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedMapKeyAnnotation();

  public final Optional<AnnotationMirror> mapKeyAnnotation() {
    return unwrapOptionalEquivalence(wrappedMapKeyAnnotation());
  }

  /** If {@link #bindingElement()} is a method that returns a primitive type, returns that type. */
  public final Optional<TypeMirror> contributedPrimitiveType() {
    return bindingElement()
        .map(XConverters::toJavac)
        .filter(bindingElement -> bindingElement.getKind() == ElementKind.METHOD)
        .map(bindingElement -> MoreElements.asExecutable(bindingElement).getReturnType())
        .filter(type -> type.getKind().isPrimitive());
  }

  @Override
  public final boolean isNullable() {
    return nullableType().isPresent();
  }

  /**
   * The {@link TypeMirror type} for the {@code Factory<T>} or {@code Producer<T>} which is created
   * for this binding. Uses the binding's key, V in the case of {@code Map<K, FrameworkClass<V>>>},
   * and E {@code Set<E>} for {@code dagger.multibindings.IntoSet @IntoSet} methods.
   */
  public final TypeMirror contributedType() {
    switch (contributionType()) {
      case UNIQUE:
        return key().type().java();
    }
    throw new AssertionError();
  }

  public abstract Builder<?, ?> toBuilder();

  /**
   * Base builder for {@code com.google.auto.value.AutoValue @AutoValue} subclasses of {@link
   * ContributionBinding}.
   */
  public abstract static class Builder<C extends ContributionBinding, B extends Builder<C, B>> {
    public abstract B dependencies(Iterable<DependencyRequest> dependencies);

    public B dependencies(DependencyRequest... dependencies) {
      return dependencies(asList(dependencies));
    }

    public abstract B unresolved(C unresolved);

    public abstract B contributionType(ContributionType contributionType);

    public abstract B bindingElement(XElement bindingElement);

    abstract B bindingElement(Optional<XElement> bindingElement);

    public final B clearBindingElement() {
      return bindingElement(Optional.empty());
    };

    abstract B contributingModule(XTypeElement contributingModule);

    public abstract B key(Key key);

    public abstract B nullableType(Optional<XType> nullableType);

    abstract B wrappedMapKeyAnnotation(
        Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedMapKeyAnnotation);

    public abstract B kind(BindingKind kind);

    abstract C build();
  }
}
