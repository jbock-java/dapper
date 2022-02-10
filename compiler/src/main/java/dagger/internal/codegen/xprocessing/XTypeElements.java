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

package dagger.internal.codegen.xprocessing;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static io.jbock.auto.common.Visibility.effectiveVisibilityOfElement;

import io.jbock.auto.common.Visibility;
import java.util.List;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XTypeElement} helper methods. */
public final class XTypeElements {

  /** Returns {@code true} if the given {@code type} has type parameters. */
  public static boolean hasTypeParameters(XTypeElement type) {
    // TODO(bcorso): Add support for XTypeElement#getTypeParameters() or at least
    // XTypeElement#hasTypeParameters() in XProcessing. XTypes#getTypeArguments() isn't quite the
    // same -- it tells you if the declared type has parameters rather than the element itself.
    return !type.toJavac().getTypeParameters().isEmpty();
  }

  /** Returns all non-private, non-static, abstract methods in {@code type}. */
  public static Visibility effectiveVisibility(XTypeElement type) {
    return effectiveVisibilityOfElement(type.toJavac());
  }

  /** Returns all non-private, non-static, abstract methods in {@code type}. */
  public static List<XMethodElement> getAllUnimplementedMethods(XTypeElement type) {
    return type.getAllNonPrivateInstanceMethods().stream()
        .filter(XHasModifiers::isAbstract)
        .collect(toImmutableList());
  }

  private XTypeElements() {}
}