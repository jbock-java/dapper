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
import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.subcomponentCreatorAnnotations;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnyAnnotationPresent;
import static dagger.internal.codegen.xprocessing.XElements.hasAnyAnnotation;
import static javax.lang.model.util.ElementFilter.typesIn;

import dagger.Component;
import dagger.Module;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.TypeName;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Utility methods related to dagger configuration annotations (e.g.: {@link Component} and {@link
 * Module}).
 */
public final class ConfigurationAnnotations {

  public static Optional<TypeElement> getSubcomponentCreator(TypeElement subcomponent) {
    Preconditions.checkArgument(subcomponentAnnotation(subcomponent).isPresent());
    for (TypeElement nestedType : typesIn(subcomponent.getEnclosedElements())) {
      if (isSubcomponentCreator(nestedType)) {
        return Optional.of(nestedType);
      }
    }
    return Optional.empty();
  }

  static boolean isSubcomponentCreator(Element element) {
    return isAnyAnnotationPresent(element, subcomponentCreatorAnnotations());
  }

  /**
   * Returns the full set of modules transitively {@linkplain Module#includes included} from the
   * given seed modules. If a module is malformed and a type listed in {@link Module#includes} is
   * not annotated with {@link Module}, it is ignored.
   *
   * @deprecated Use {@link ComponentDescriptor#modules()}.
   */
  @Deprecated
  public static Set<TypeElement> getTransitiveModules(
      DaggerTypes types, DaggerElements elements, Set<TypeElement> seedModules) {
    TypeMirror objectType = elements.getTypeElement(TypeName.OBJECT).asType();
    Queue<TypeElement> moduleQueue = new ArrayDeque<>(seedModules);
    Set<TypeElement> moduleElements = new LinkedHashSet<>();
    TypeElement moduleElement;
    while ((moduleElement = moduleQueue.poll()) != null) {
      TypeElement mel = moduleElement;
      moduleAnnotation(moduleElement)
          .ifPresent(
              moduleAnnotation -> {
                Set<TypeElement> moduleDependencies =
                    new LinkedHashSet<>();
                moduleDependencies.addAll(moduleAnnotation.includes());
                // We don't recur on the parent class because we don't want the parent class as a
                // root that the component depends on, and also because we want the dependencies
                // rooted against this element, not the parent.
                addIncludesFromSuperclasses(
                    types, mel, moduleDependencies, objectType);
                moduleElements.add(mel);
                for (TypeElement dependencyType : moduleDependencies) {
                  if (!moduleElements.contains(dependencyType)) {
                    moduleQueue.add(dependencyType);
                  }
                }
              });
    }
    return moduleElements;
  }

  /** Returns the enclosed types annotated with the given annotation. */
  public static Set<XTypeElement> enclosedAnnotatedTypes(
      XTypeElement typeElement, Set<ClassName> annotations) {
    return typeElement.getEnclosedTypeElements().stream()
        .filter(enclosedType -> hasAnyAnnotation(enclosedType, annotations))
        .collect(toImmutableSet());
  }

  // TODO(bcorso): Migrate users to the XProcessing version above.
  /** Returns the enclosed types annotated with the given annotation. */
  public static Set<TypeElement> enclosedAnnotatedTypes(
      TypeElement typeElement, Set<ClassName> annotations) {
    return typesIn(typeElement.getEnclosedElements()).stream()
        .filter(enclosedType -> isAnyAnnotationPresent(enclosedType, annotations))
        .collect(toImmutableSet());
  }
  /** Traverses includes from superclasses and adds them into the builder. */
  private static void addIncludesFromSuperclasses(
      DaggerTypes types,
      TypeElement element,
      Set<TypeElement> builder,
      TypeMirror objectType) {
    // Also add the superclass to the queue, in case any @Module definitions were on that.
    TypeMirror superclass = element.getSuperclass();
    while (!types.isSameType(objectType, superclass)
        && superclass.getKind().equals(TypeKind.DECLARED)) {
      element = MoreElements.asType(types.asElement(superclass));
      moduleAnnotation(element)
          .ifPresent(moduleAnnotation -> builder.addAll(moduleAnnotation.includes()));
      superclass = element.getSuperclass();
    }
  }

  private ConfigurationAnnotations() {
  }
}
