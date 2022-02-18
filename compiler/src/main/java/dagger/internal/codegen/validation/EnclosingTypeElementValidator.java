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

package dagger.internal.codegen.validation;

import static dagger.internal.codegen.langmodel.DaggerElements.closestEnclosingTypeElement;

import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.xprocessing.XConverters;
import dagger.internal.codegen.xprocessing.XElement;
import io.jbock.auto.common.SuperficialValidation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** Validates enclosing type elements in a round. */
@Singleton
public final class EnclosingTypeElementValidator implements ClearableCache {

  private final Map<TypeElement, Boolean> validatedTypeElements = new HashMap<>();

  @Inject
  EnclosingTypeElementValidator() {
  }

  public void validateEnclosingType(XElement element) {
    Element javaElement = XConverters.toJavac(element);
    if (!validatedTypeElements.computeIfAbsent(
        closestEnclosingTypeElement(javaElement), SuperficialValidation::validateElement)) {
      throw new TypeNotPresentException(element.toString(), null);
    }
  }

  @Override
  public void clearCache() {
    validatedTypeElements.clear();
  }
}