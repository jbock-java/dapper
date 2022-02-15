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

import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotation;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.subcomponentCreatorAnnotations;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.hasAnyAnnotation;

import dagger.Component;
import dagger.Module;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.javapoet.ClassName;
import java.util.Optional;
import java.util.Set;

/**
 * Utility methods related to dagger configuration annotations (e.g.: {@link Component} and {@link
 * Module}).
 */
public final class ConfigurationAnnotations {

  public static Optional<XTypeElement> getSubcomponentCreator(XTypeElement subcomponent) {
    Preconditions.checkArgument(subcomponentAnnotation(subcomponent).isPresent());
    return subcomponent.getEnclosedTypeElements().stream()
        .filter(ConfigurationAnnotations::isSubcomponentCreator)
        // TODO(bcorso): Consider doing toOptional() instead since there should be at most 1.
        .findFirst();
  }

  static boolean isSubcomponentCreator(XElement element) {
    return hasAnyAnnotation(element, subcomponentCreatorAnnotations());
  }

  /** Returns the enclosed types annotated with the given annotation. */
  public static Set<XTypeElement> enclosedAnnotatedTypes(
      XTypeElement typeElement, Set<ClassName> annotations) {
    return typeElement.getEnclosedTypeElements().stream()
        .filter(enclosedType -> hasAnyAnnotation(enclosedType, annotations))
        .collect(toImmutableSet());
  }

  private ConfigurationAnnotations() {
  }
}
