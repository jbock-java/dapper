/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static dagger.internal.codegen.base.Util.getOnlyElement;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static java.util.Objects.requireNonNull;

import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.javapoet.CodeBlocks;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.model.DependencyRequest;

/** A framework instance creation expression for a {@link dagger.Binds @Binds} binding. */
final class DelegatingFrameworkInstanceCreationExpression
    implements FrameworkInstanceCreationExpression {

  private final ContributionBinding binding;
  private final ComponentImplementation componentImplementation;
  private final ComponentRequestRepresentations componentRequestRepresentations;

  @AssistedInject
  DelegatingFrameworkInstanceCreationExpression(
      @Assisted ContributionBinding binding,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations) {
    this.binding = requireNonNull(binding);
    this.componentImplementation = componentImplementation;
    this.componentRequestRepresentations = componentRequestRepresentations;
  }

  @Override
  public CodeBlock creationExpression() {
    DependencyRequest dependency = getOnlyElement(binding.dependencies());
    return CodeBlocks.cast(
        componentRequestRepresentations
            .getDependencyExpression(
                bindingRequest(dependency.key(), binding.frameworkType()),
                componentImplementation.shardImplementation(binding).name())
            .codeBlock(),
        binding.frameworkType().frameworkClass());
  }

  @Override
  public boolean useSwitchingProvider() {
    // For delegate expressions, we just want to return the delegate field directly using the above
    // creationExpression(). Using SwitchingProviders would be less efficient because it would
    // create a new SwitchingProvider that just returns "delegateField.get()".
    return false;
  }

  @AssistedFactory
  interface Factory {
    DelegatingFrameworkInstanceCreationExpression create(ContributionBinding binding);
  }
}
