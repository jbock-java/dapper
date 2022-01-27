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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.base.RequestKinds.requestType;
import static java.util.Objects.requireNonNull;

import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import dagger.model.RequestKind;
import dagger.spi.model.Key;
import java.util.Objects;
import javax.lang.model.type.TypeMirror;

/**
 * A request for a binding, which may be in the form of a request for a dependency to pass to a
 * constructor or module method ({@link RequestKind}) or an internal request for a framework
 * instance ({@link FrameworkType}).
 */
public final class BindingRequest {
  private final Key key;
  private final RequestKind requestKind;

  private BindingRequest(
      Key key,
      RequestKind requestKind) {
    this.key = requireNonNull(key);
    this.requestKind = requireNonNull(requestKind);
  }

  /** Creates a {@link BindingRequest} for the given {@link DependencyRequest}. */
  public static BindingRequest bindingRequest(DependencyRequest dependencyRequest) {
    return bindingRequest(dependencyRequest.key(), dependencyRequest.kind());
  }

  /**
   * Creates a {@link BindingRequest} for a normal dependency request for the given {@link Key} and
   * {@link RequestKind}.
   */
  public static BindingRequest bindingRequest(Key key, RequestKind requestKind) {
    // When there's a request that has a 1:1 mapping to a FrameworkType, the request should be
    // associated with that FrameworkType as well, because we want to ensure that if a request
    // comes in for that as a dependency first and as a framework instance later, they resolve to
    // the same binding expression.
    // TODO(cgdecker): Instead of doing this, make ComponentBindingExpressions create a
    // BindingExpression for the RequestKind that simply delegates to the BindingExpression for the
    // FrameworkType. Then there are separate BindingExpressions, but we don't end up doing weird
    // things like creating two fields when there should only be one.
    return new BindingRequest(key, requestKind);
  }

  /**
   * Creates a {@link BindingRequest} for a request for a framework instance for the given {@link
   * Key} with the given {@link FrameworkType}.
   */
  public static BindingRequest bindingRequest(Key key, FrameworkType frameworkType) {
    return new BindingRequest(key, frameworkType.requestKind());
  }

  /** Returns the {@link Key} for the requested binding. */
  public Key key() {
    return key;
  }

  /** Returns the request kind associated with this request. */
  public RequestKind requestKind() {
    return requestKind;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BindingRequest that = (BindingRequest) o;
    return key.equals(that.key)
        && requestKind == that.requestKind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, requestKind);
  }

  /** Returns whether this request is of the given kind. */
  public boolean isRequestKind(RequestKind requestKind) {
    return requestKind.equals(requestKind());
  }

  public TypeMirror requestedType(TypeMirror contributedType, DaggerTypes types) {
    return requestType(requestKind(), contributedType, types);
  }
}
