/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static dagger.internal.codegen.base.MoreAnnotationMirrors.wrapOptionalInEquivalence;
import static dagger.internal.codegen.base.Preconditions.checkArgument;
import static dagger.internal.codegen.binding.MapKeys.getMapKey;

import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.ContributionType.HasContributionType;
import dagger.internal.codegen.collect.Iterables;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XConverters;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XMethodType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.spi.model.DependencyRequest;
import io.jbock.auto.common.Equivalence;
import io.jbock.auto.value.AutoValue;
import io.jbock.auto.value.extension.memoized.Memoized;
import jakarta.inject.Inject;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;

/** The declaration for a delegate binding established by a {@code Binds} method. */
@AutoValue
public abstract class DelegateDeclaration extends BindingDeclaration
    implements HasContributionType {
  abstract DependencyRequest delegateRequest();

  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedMapKey();

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

  /** A {@code DelegateDeclaration} factory. */
  public static final class Factory {
    private final KeyFactory keyFactory;
    private final DependencyRequestFactory dependencyRequestFactory;

    @Inject
    Factory(
        KeyFactory keyFactory,
        DependencyRequestFactory dependencyRequestFactory) {
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    public DelegateDeclaration create(XMethodElement bindsMethod, XTypeElement contributingModule) {
      checkArgument(bindsMethod.hasAnnotation(TypeNames.BINDS));
      XMethodType resolvedMethod = bindsMethod.asMemberOf(contributingModule.getType());
      DependencyRequest delegateRequest =
          dependencyRequestFactory.forRequiredResolvedVariable(
              Iterables.getOnlyElement(bindsMethod.getParameters()),
              Iterables.getOnlyElement(resolvedMethod.getParameterTypes()));
      return new AutoValue_DelegateDeclaration(
          ContributionType.fromBindingElement(bindsMethod),
          keyFactory.forBindsMethod(bindsMethod, contributingModule),
          Optional.<XElement>of(bindsMethod),
          Optional.of(contributingModule),
          delegateRequest,
          wrapOptionalInEquivalence(getMapKey(bindsMethod).map(XConverters::toJavac)));
    }
  }
}
