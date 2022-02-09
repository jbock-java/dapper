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

import dagger.Provides;
import dagger.spi.model.DaggerElement;
import dagger.spi.model.Key;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.Element;

/**
 * Represents a request for a {@link Key} at an injection point. For example, parameters to {@code
 * Inject} constructors, {@link Provides} methods, and component methods are all dependency
 * requests.
 *
 * <p id="synthetic">A dependency request is considered to be <em>synthetic</em> if it does not have
 * an {@link Element} in code that requests the key directly. For example, an {@link
 * java.util.concurrent.Executor} is required for all {@code @Produces} methods to run
 * asynchronously even though it is not directly specified as a parameter to the binding method.
 */
public final class DependencyRequest {

  private final RequestKind kind;
  private final Key key;
  private final Optional<DaggerElement> requestElement;

  private DependencyRequest(
      RequestKind kind,
      Key key,
      Optional<DaggerElement> requestElement) {
    this.kind = requireNonNull(kind);
    this.key = requireNonNull(key);
    this.requestElement = requireNonNull(requestElement);
  }

  /** The kind of this request. */
  public RequestKind kind() {
    return kind;
  }

  /** The key of this request. */
  public Key key() {
    return key;
  }

  /**
   * The element that declares this dependency request. Absent for <a href="#synthetic">synthetic
   * </a> requests.
   */
  public Optional<DaggerElement> requestElement() {
    return requestElement;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DependencyRequest that = (DependencyRequest) o;
    return kind == that.kind && key.equals(that.key) && requestElement.equals(that.requestElement);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, key, requestElement);
  }

  /** Returns a new builder of dependency requests. */
  public static DependencyRequest.Builder builder() {
    return new Builder();
  }

  /** A builder of {@link DependencyRequest}s. */
  public static final class Builder {
    private RequestKind kind;
    private Key key;
    private Optional<DaggerElement> requestElement = Optional.empty();

    private Builder() {
    }

    public Builder kind(RequestKind kind) {
      this.kind = kind;
      return this;
    }

    public Builder key(Key key) {
      this.key = key;
      return this;
    }

    public Builder requestElement(DaggerElement requestElement) {
      this.requestElement = Optional.of(requestElement);
      return this;
    }

    public DependencyRequest build() {
      return new DependencyRequest(
          this.kind,
          this.key,
          this.requestElement);
    }
  }
}
