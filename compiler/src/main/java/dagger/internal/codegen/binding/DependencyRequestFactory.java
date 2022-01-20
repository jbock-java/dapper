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

import static dagger.internal.codegen.base.RequestKinds.extractKeyType;
import static dagger.internal.codegen.base.RequestKinds.getRequestKind;
import static java.util.Objects.requireNonNull;

import dagger.internal.codegen.base.Preconditions;
import dagger.model.DependencyRequest;
import dagger.model.RequestKind;
import jakarta.inject.Inject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/**
 * Factory for {@link DependencyRequest}s.
 *
 * <p>Any factory method may throw {@link TypeNotPresentException} if a type is not available, which
 * may mean that the type will be generated in a later round of processing.
 */
public final class DependencyRequestFactory {
  private final KeyFactory keyFactory;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  DependencyRequestFactory(KeyFactory keyFactory, InjectionAnnotations injectionAnnotations) {
    this.keyFactory = keyFactory;
    this.injectionAnnotations = injectionAnnotations;
  }

  Set<DependencyRequest> forRequiredResolvedVariables(
      List<? extends VariableElement> variables, List<? extends TypeMirror> resolvedTypes) {
    Preconditions.checkState(resolvedTypes.size() == variables.size());
    Set<DependencyRequest> builder = new LinkedHashSet<>();
    for (int i = 0; i < variables.size(); i++) {
      builder.add(forRequiredResolvedVariable(variables.get(i), resolvedTypes.get(i)));
    }
    return builder;
  }

  DependencyRequest forRequiredResolvedVariable(
      VariableElement variableElement, TypeMirror resolvedType) {
    requireNonNull(variableElement);
    requireNonNull(resolvedType);
    // Ban @Assisted parameters, they are not considered dependency requests.
    Preconditions.checkArgument(!AssistedInjectionAnnotations.isAssistedParameter(variableElement));
    Optional<AnnotationMirror> qualifier = injectionAnnotations.getQualifier(variableElement);
    return newDependencyRequest(variableElement, resolvedType, qualifier);
  }

  public DependencyRequest forComponentProvisionMethod(
      ExecutableElement provisionMethod, ExecutableType provisionMethodType) {
    requireNonNull(provisionMethod);
    requireNonNull(provisionMethodType);
    Preconditions.checkArgument(
        provisionMethod.getParameters().isEmpty(),
        "Component provision methods must be empty: %s",
        provisionMethod);
    Optional<AnnotationMirror> qualifier = injectionAnnotations.getQualifier(provisionMethod);
    return newDependencyRequest(provisionMethod, provisionMethodType.getReturnType(), qualifier);
  }

  private DependencyRequest newDependencyRequest(
      Element requestElement, TypeMirror type, Optional<AnnotationMirror> qualifier) {
    RequestKind requestKind = getRequestKind(type);
    return DependencyRequest.builder()
        .kind(requestKind)
        .key(keyFactory.forQualifiedType(qualifier, extractKeyType(type)))
        .requestElement(requestElement)
        .build();
  }
}
