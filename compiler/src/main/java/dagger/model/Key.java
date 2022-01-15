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

package dagger.model;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.Equivalence.Wrapper;
import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.CodeBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

/**
 * A {@linkplain TypeMirror type} and an optional {@linkplain jakarta.inject.Qualifier qualifier} that
 * is the lookup key for a binding.
 */
public final class Key {
  private final Optional<Wrapper<AnnotationMirror>> wrappedQualifier;
  private final Wrapper<TypeMirror> wrappedType;

  private final int hashCode;

  private Key(
      Optional<Wrapper<AnnotationMirror>> wrappedQualifier,
      Wrapper<TypeMirror> wrappedType) {
    this.wrappedQualifier = requireNonNull(wrappedQualifier);
    this.wrappedType = requireNonNull(wrappedType);
    this.hashCode = Objects.hash(wrappedQualifier, wrappedType);
  }

  /**
   * A {@link jakarta.inject.Qualifier} annotation that provides a unique namespace prefix
   * for the type of this key.
   */
  public Optional<AnnotationMirror> qualifier() {
    return wrappedQualifier().map(Wrapper::get);
  }

  /**
   * The type represented by this key.
   */
  public TypeMirror type() {
    return wrappedType().get();
  }

  /**
   * A {@link jakarta.inject.Qualifier} annotation that provides a unique namespace prefix
   * for the type of this key.
   *
   * Despite documentation in {@link AnnotationMirror}, equals and hashCode aren't implemented
   * to represent logical equality, so {@link AnnotationMirrors#equivalence()}
   * provides this facility.
   */
  Optional<Wrapper<AnnotationMirror>> wrappedQualifier() {
    return wrappedQualifier;
  }

  /**
   * The type represented by this key.
   *
   * As documented in {@link TypeMirror}, equals and hashCode aren't implemented to represent
   * logical equality, so {@link MoreTypes#equivalence()} wraps this type.
   */
  Wrapper<TypeMirror> wrappedType() {
    return wrappedType;
  }

  /**
   * Distinguishes keys for multibinding contributions that share a {@link #type()} and {@link
   * #qualifier()}.
   *
   * <p>Each multibound map and set has a synthetic multibinding that depends on the specific
   * contributions to that map or set using keys that identify those multibinding contributions.
   *
   * <p>Absent except for multibinding contributions.
   */
  public Optional<MultibindingContributionIdentifier> multibindingContributionIdentifier() {
    return Optional.empty();
  }

  /** Returns a {@link Builder} that inherits the properties of this key. */
  public Builder toBuilder() {
    return new Builder(this);
  }

  // The main hashCode/equality bottleneck is in MoreTypes.equivalence(). It's possible that we can
  // avoid this by tuning that method. Perhaps we can also avoid the issue entirely by interning all
  // Keys
  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Key)) {
      return false;
    }
    Key that = (Key) o;
    if (this.hashCode != that.hashCode) {
      return false;
    }
    return Objects.equals(this.wrappedQualifier, that.wrappedQualifier())
        && Objects.equals(this.wrappedType, that.wrappedType());
  }

  /**
   * Returns a String rendering of an {@link AnnotationMirror} that includes attributes in the order
   * defined in the annotation type. This will produce the same output for {@linkplain
   * AnnotationMirrors#equivalence() equal} {@link AnnotationMirror}s even if default values are
   * omitted or their attributes were written in different orders, e.g. {@code @A(b = "b", c = "c")}
   * and {@code @A(c = "c", b = "b", attributeWithDefaultValue = "default value")}.
   */
  // TODO(ronshapiro): move this to auto-common
  static String stableAnnotationMirrorToString(AnnotationMirror qualifier) {
    StringBuilder builder = new StringBuilder("@").append(qualifier.getAnnotationType());
    Map<ExecutableElement, AnnotationValue> elementValues =
        AnnotationMirrors.getAnnotationValuesWithDefaults(qualifier);
    if (!elementValues.isEmpty()) {
      Map<String, String> namedValues = new LinkedHashMap<>();
      elementValues.forEach(
          (key, value) ->
              namedValues.put(
                  key.getSimpleName().toString(), stableAnnotationValueToString(value)));
      builder.append('(');
      if (namedValues.size() == 1 && namedValues.containsKey("value")) {
        // Omit "value ="
        builder.append(namedValues.get("value"));
      } else {
        builder.append(namedValues.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(joining(", ")));
      }
      builder.append(')');
    }
    return builder.toString();
  }

  private static String stableAnnotationValueToString(AnnotationValue annotationValue) {
    return annotationValue.accept(
        new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          protected String defaultAction(Object value, Void ignore) {
            return value.toString();
          }

          @Override
          public String visitString(String value, Void ignore) {
            return CodeBlock.of("$S", value).toString();
          }

          @Override
          public String visitAnnotation(AnnotationMirror value, Void ignore) {
            return stableAnnotationMirrorToString(value);
          }

          @Override
          public String visitArray(List<? extends AnnotationValue> value, Void ignore) {
            return value.stream()
                .map(Key::stableAnnotationValueToString)
                .collect(joining(", ", "{", "}"));
          }
        },
        null);
  }

  @Override
  public String toString() {
    return Stream.of(
            qualifier().map(Key::stableAnnotationMirrorToString).orElse(null),
            type(),
            multibindingContributionIdentifier().orElse(null))
        .filter(Objects::nonNull)
        .map(Objects::toString)
        .collect(Collectors.joining(" "));
  }

  /** Returns a builder for {@link Key}s. */
  public static Builder builder(TypeMirror type) {
    return new Builder().type(type);
  }

  /** A builder for {@link Key}s. */
  public static final class Builder {

    private Optional<Wrapper<AnnotationMirror>> wrappedQualifier = Optional.empty();
    private Wrapper<TypeMirror> wrappedType;

    private Builder() {
    }

    private Builder(Key source) {
      this.wrappedQualifier = source.wrappedQualifier();
      this.wrappedType = source.wrappedType();
    }

    public Builder type(TypeMirror type) {
      this.wrappedType = MoreTypes.equivalence().wrap(requireNonNull(type));
      return this;
    }

    public Builder qualifier(AnnotationMirror qualifier) {
      this.wrappedQualifier = Optional.of(AnnotationMirrors.equivalence().wrap(requireNonNull(qualifier)));
      return this;
    }

    public Builder qualifier(Optional<AnnotationMirror> qualifier) {
      this.wrappedQualifier = qualifier.map(AnnotationMirrors.equivalence()::wrap);
      return this;
    }

    public Key build() {
      return new Key(
          this.wrappedQualifier,
          this.wrappedType);
    }
  }

  /**
   * An object that identifies a multibinding contribution method and the module class that
   * contributes it to the graph.
   *
   * @see #multibindingContributionIdentifier()
   */
  public static final class MultibindingContributionIdentifier {
  }
}
