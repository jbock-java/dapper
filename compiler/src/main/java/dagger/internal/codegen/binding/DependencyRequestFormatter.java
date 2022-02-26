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

import static dagger.internal.codegen.base.ElementFormatter.elementToString;
import static dagger.internal.codegen.base.RequestKinds.requestType;
import static dagger.internal.codegen.xprocessing.XElement.isMethod;
import static dagger.internal.codegen.xprocessing.XElement.isTypeElement;
import static dagger.internal.codegen.xprocessing.XElement.isVariableElement;

import dagger.internal.codegen.base.Formatter;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.DependencyRequest;
import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Formats a {@code DependencyRequest} into a {@code String} suitable for an error message listing a
 * chain of dependencies.
 *
 * <dl>
 *   <dt>For component provision methods
 *   <dd>{@code @Qualifier SomeType is provided at\n ComponentType.method()}
 *   <dt>For component injection methods
 *   <dd>{@code SomeType is injected at\n ComponentType.method(foo)}
 *   <dt>For parameters to {@code Provides @Provides}, {@code Produces @Produces}, or {@code
 *       Inject @Inject} methods:
 *   <dd>{@code @Qualified ResolvedType is injected at\n EnclosingType.method([…, ]param[, …])}
 *   <dt>For parameters to {@code Inject @Inject} constructors:
 *   <dd>{@code @Qualified ResolvedType is injected at\n EnclosingType([…, ]param[, …])}
 *   <dt>For {@code Inject @Inject} fields:
 *   <dd>{@code @Qualified ResolvedType is injected at\n EnclosingType.field}
 * </dl>
 */
public final class DependencyRequestFormatter extends Formatter<DependencyRequest> {

  private final XProcessingEnv processingEnv;

  @Inject
  DependencyRequestFormatter(XProcessingEnv processingEnv) {
    this.processingEnv = processingEnv;
  }

  @Override
  public String format(DependencyRequest request) {
    if (!request.requestElement().isPresent()) {
      return "";
    }
    XElement requestElement = request.requestElement().get().xprocessing();
    if (isMethod(requestElement)) {
      return INDENT
          + request.key()
          + " is "
          + componentMethodRequestVerb(request)
          + " at\n"
          + DOUBLE_INDENT
          + elementToString(requestElement);
    } else if (isVariableElement(requestElement)) {
      return INDENT
          + formatQualifier(request.key().qualifier())
          + requestType(request.kind(), request.key().type().xprocessing(), processingEnv)
          + " is injected at\n"
          + DOUBLE_INDENT
          + elementToString(requestElement);
    } else if (isTypeElement(requestElement)) {
      return ""; // types by themselves provide no useful information.
    } else {
      throw new IllegalStateException("Invalid request element " + requestElement);
    }
  }

  /**
   * Appends a newline and the formatted dependency request unless {@code
   * #format(DependencyRequest)} returns the empty string.
   */
  public StringBuilder appendFormatLine(
      StringBuilder builder, DependencyRequest dependencyRequest) {
    String formatted = format(dependencyRequest);
    if (!formatted.isEmpty()) {
      builder.append('\n').append(formatted);
    }
    return builder;
  }

  private String formatQualifier(Optional<DaggerAnnotation> maybeQualifier) {
    return maybeQualifier.map(qualifier -> qualifier + " ").orElse("");
  }

  /**
   * Returns the verb for a component method dependency request. Returns "produced", "provided", or
   * "injected", depending on the kind of request.
   */
  private String componentMethodRequestVerb(DependencyRequest request) {
    switch (request.kind()) {
      case FUTURE:
      case PRODUCER:
      case INSTANCE:
      case LAZY:
      case PROVIDER:
      case PROVIDER_OF_LAZY:
        return "requested";

      case MEMBERS_INJECTION:
        return "injected";

      case PRODUCED:
        break;
    }
    throw new AssertionError("illegal request kind for method: " + request);
  }
}
