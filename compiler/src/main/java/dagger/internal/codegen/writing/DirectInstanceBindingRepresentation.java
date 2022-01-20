/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.internal.codegen.writing;

import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.model.BindingKind;
import dagger.model.RequestKind;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A provision binding representation that returns expressions based on a direct instance
 * expression.
 */
final class DirectInstanceBindingRepresentation implements BindingRepresentation {
  private final ProvisionBinding binding;
  private final BindingGraph graph;
  private final ComponentImplementation componentImplementation;
  private final ComponentMethodRequestRepresentation.Factory
      componentMethodRequestRepresentationFactory;
  private final PrivateMethodRequestRepresentation.Factory
      privateMethodRequestRepresentationFactory;
  private final AssistedPrivateMethodRequestRepresentation.Factory
      assistedPrivateMethodRequestRepresentationFactory;
  private final UnscopedDirectInstanceRequestRepresentationFactory
      unscopedDirectInstanceRequestRepresentationFactory;
  private final Map<BindingRequest, RequestRepresentation> requestRepresentations = new HashMap<>();

  @AssistedInject
  DirectInstanceBindingRepresentation(
      @Assisted ProvisionBinding binding,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      ComponentMethodRequestRepresentation.Factory componentMethodRequestRepresentationFactory,
      PrivateMethodRequestRepresentation.Factory privateMethodRequestRepresentationFactory,
      AssistedPrivateMethodRequestRepresentation.Factory
          assistedPrivateMethodRequestRepresentationFactory,
      UnscopedDirectInstanceRequestRepresentationFactory
          unscopedDirectInstanceRequestRepresentationFactory) {
    this.binding = binding;
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.componentMethodRequestRepresentationFactory = componentMethodRequestRepresentationFactory;
    this.privateMethodRequestRepresentationFactory = privateMethodRequestRepresentationFactory;
    this.unscopedDirectInstanceRequestRepresentationFactory =
        unscopedDirectInstanceRequestRepresentationFactory;
    this.assistedPrivateMethodRequestRepresentationFactory =
        assistedPrivateMethodRequestRepresentationFactory;
  }

  @Override
  public RequestRepresentation getRequestRepresentation(BindingRequest request) {
    return reentrantComputeIfAbsent(
        requestRepresentations, request, this::getRequestRepresentationUncached);
  }

  private RequestRepresentation getRequestRepresentationUncached(BindingRequest request) {
    switch (request.requestKind()) {
      case INSTANCE:
        return instanceRequestRepresentation();

      default:
        throw new AssertionError(
            String.format("Invalid binding request kind: %s", request.requestKind()));
    }
  }

  private RequestRepresentation instanceRequestRepresentation() {
    RequestRepresentation directInstanceExpression =
        unscopedDirectInstanceRequestRepresentationFactory.create(binding);
    if (binding.kind() == BindingKind.ASSISTED_INJECTION) {
      BindingRequest request = bindingRequest(binding.key(), RequestKind.INSTANCE);
      return assistedPrivateMethodRequestRepresentationFactory.create(
          request, binding, directInstanceExpression);
    }
    return ProvisionBindingRepresentation.requiresMethodEncapsulation(binding)
        ? wrapInMethod(RequestKind.INSTANCE, directInstanceExpression)
        : directInstanceExpression;
  }

  /**
   * Returns a binding expression that uses a given one as the body of a method that users call. If
   * a component provision method matches it, it will be the method implemented. If it does not
   * match a component provision method and the binding is modifiable, then a new public modifiable
   * binding method will be written. If the binding doesn't match a component method and is not
   * modifiable, then a new private method will be written.
   */
  RequestRepresentation wrapInMethod(
      RequestKind requestKind, RequestRepresentation bindingExpression) {
    // If we've already wrapped the expression, then use the delegate.
    if (bindingExpression instanceof MethodRequestRepresentation) {
      return bindingExpression;
    }

    BindingRequest request = bindingRequest(binding.key(), requestKind);
    Optional<ComponentMethodDescriptor> matchingComponentMethod =
        graph.componentDescriptor().firstMatchingComponentMethod(request);

    ShardImplementation shardImplementation = componentImplementation.shardImplementation(binding);

    // Consider the case of a request from a component method like:
    //
    //   DaggerMyComponent extends MyComponent {
    //     @Overrides
    //     Foo getFoo() {
    //       <FOO_BINDING_REQUEST>
    //     }
    //   }
    //
    // Normally, in this case we would return a ComponentMethodRequestRepresentation rather than a
    // PrivateMethodRequestRepresentation so that #getFoo() can inline the implementation rather
    // than
    // create an unnecessary private method and return that. However, with sharding we don't want to
    // inline the implementation because that would defeat some of the class pool savings if those
    // fields had to communicate across shards. Thus, when a key belongs to a separate shard use a
    // PrivateMethodRequestRepresentation and put the private method in the shard.
    if (matchingComponentMethod.isPresent() && shardImplementation.isComponentShard()) {
      ComponentMethodDescriptor componentMethod = matchingComponentMethod.get();
      return componentMethodRequestRepresentationFactory.create(bindingExpression, componentMethod);
    } else {
      return privateMethodRequestRepresentationFactory.create(request, binding, bindingExpression);
    }
  }

  @AssistedFactory
  interface Factory {
    DirectInstanceBindingRepresentation create(ProvisionBinding binding);
  }
}