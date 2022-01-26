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

package dagger.spi.model;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@linkplain DaggerType type} and an optional {@linkplain jakarta.inject.Qualifier qualifier} that
 * is the lookup key for a binding.
 */
public final class Key {
  private final Optional<DaggerAnnotation> qualifier;
  private final DaggerType type;

  private final int hashCode;

  private Key(
      Optional<DaggerAnnotation> qualifier,
      DaggerType type) {
    this.qualifier = requireNonNull(qualifier);
    this.type = requireNonNull(type);
    this.hashCode = Objects.hash(qualifier, type);
  }

  /**
   * A {@link jakarta.inject.Qualifier} annotation that provides a unique namespace prefix
   * for the type of this key.
   */
  public Optional<DaggerAnnotation> qualifier() {
    return qualifier;
  }

  /**
   * The type represented by this key.
   */
  public DaggerType type() {
    return type;
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
    return Objects.equals(this.qualifier, that.qualifier)
        && Objects.equals(this.type, that.type);
  }

  @Override
  public String toString() {
    return Stream.of(
            qualifier().map(MoreAnnotationMirrors::toStableString).orElse(null),
            type(),
            multibindingContributionIdentifier().orElse(null))
        .filter(Objects::nonNull)
        .map(Objects::toString)
        .collect(Collectors.joining(" "));
  }

  /** Returns a builder for {@link Key}s. */
  public static Builder builder(DaggerType type) {
    return new Builder().type(type);
  }

  /** A builder for {@link Key}s. */
  public static final class Builder {

    private Optional<DaggerAnnotation> wrappedQualifier = Optional.empty();
    private DaggerType wrappedType;

    private Builder() {
    }

    public Builder type(DaggerType type) {
      this.wrappedType = type;
      return this;
    }

    public Builder qualifier(DaggerAnnotation qualifier) {
      this.wrappedQualifier = Optional.of(qualifier);
      return this;
    }

    public Builder qualifier(Optional<DaggerAnnotation> qualifier) {
      this.wrappedQualifier = qualifier;
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
