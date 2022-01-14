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

package dagger.internal.codegen.validation;

import static javax.lang.model.util.ElementFilter.methodsIn;

import dagger.MapKey;
import jakarta.inject.Inject;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;

/**
 * A validator for {@link MapKey} annotations.
 */
// TODO(dpb,gak): Should unwrapped MapKeys be required to have their single member be named "value"?
public final class MapKeyValidator {

  @Inject
  MapKeyValidator() {
  }

  public ValidationReport<Element> validate(Element element) {
    List<ExecutableElement> members = methodsIn(element.getEnclosedElements());
    if (members.isEmpty()) {
      return ValidationReport.about(element)
          .addError("Map key annotations must have members", element)
          .build();
    }
    if (members.size() > 1) {
      return ValidationReport.about(element)
          .addError("Map key annotations with unwrapped values must have exactly one member", element)
          .build();
    }
    if (members.get(0).getReturnType().getKind() == TypeKind.ARRAY) {
      return ValidationReport.about(element)
          .addError("Map key annotations with unwrapped values cannot use arrays", element)
          .build();
    }
    return ValidationReport.about(element).build();
  }
}
