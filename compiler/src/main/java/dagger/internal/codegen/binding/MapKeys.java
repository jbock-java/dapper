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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.base.MapKeyAccessibility.isMapKeyPubliclyAccessible;
import static dagger.internal.codegen.base.Preconditions.checkArgument;
import static dagger.internal.codegen.binding.SourceFiles.elementBasedClassName;
import static dagger.internal.codegen.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.xprocessing.XConverters.toXProcessing;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XType.isArray;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isPrimitive;
import static io.jbock.auto.common.AnnotationMirrors.getAnnotatedAnnotations;
import static io.jbock.auto.common.AnnotationMirrors.getAnnotationValuesWithDefaults;
import static io.jbock.auto.common.MoreElements.asExecutable;
import static io.jbock.javapoet.MethodSpec.methodBuilder;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import dagger.MapKey;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.base.MapKeyAccessibility;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XAnnotation;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.MethodSpec;
import io.jbock.javapoet.TypeName;
import java.util.NoSuchElementException;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

/** Methods for extracting {@code MapKey} annotations and key code blocks from binding elements. */
public final class MapKeys {

  /**
   * If {@code bindingElement} is annotated with a {@code MapKey} annotation, returns it.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one {@code MapKey}
   *     annotation
   */
  static Optional<AnnotationMirror> getMapKey(XElement bindingElement) {
    return getMapKey(toJavac(bindingElement));
  }

  /**
   * If {@code bindingElement} is annotated with a {@code MapKey} annotation, returns it.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one {@code MapKey}
   *     annotation
   */
  static Optional<AnnotationMirror> getMapKey(Element bindingElement) {
    ImmutableSet<? extends AnnotationMirror> mapKeys = getMapKeys(bindingElement);
    return mapKeys.isEmpty()
        ? Optional.empty()
        : Optional.<AnnotationMirror>of(getOnlyElement(mapKeys));
  }

  /** Returns all of the {@code MapKey} annotations that annotate {@code bindingElement}. */
  public static ImmutableSet<? extends AnnotationMirror> getMapKeys(Element bindingElement) {
    return ImmutableSet.copyOf(getAnnotatedAnnotations(bindingElement, MapKey.class));
  }

  /** Returns all of the {@code MapKey} annotations that annotate {@code bindingElement}. */
  public static ImmutableSet<XAnnotation> getMapKeys(XElement bindingElement) {
    return XElements.getAnnotatedAnnotations(bindingElement, TypeNames.MAP_KEY);
  }

  /**
   * Returns the annotation value if {@code mapKey}'s type is annotated with {@code
   * MapKey @MapKey(unwrapValue = true)}.
   *
   * @throws IllegalArgumentException if {@code mapKey}'s type is not annotated with {@code
   *     MapKey @MapKey} at all.
   */
  static Optional<? extends AnnotationValue> unwrapValue(AnnotationMirror mapKey) {
    MapKey mapKeyAnnotation = mapKey.getAnnotationType().asElement().getAnnotation(MapKey.class);
    checkArgument(
        mapKeyAnnotation != null, "%s is not annotated with @MapKey", mapKey.getAnnotationType());
    return mapKeyAnnotation.unwrapValue()
        ? Optional.of(getOnlyElement(getAnnotationValuesWithDefaults(mapKey).values()))
        : Optional.empty();
  }

  static TypeMirror mapKeyType(XAnnotation mapKeyAnnotation) {
    return unwrapValue(toJavac(mapKeyAnnotation)).isPresent()
        ? toJavac(getUnwrappedMapKeyType(mapKeyAnnotation.getType()))
        : toJavac(mapKeyAnnotation.getType());
  }

  /**
   * Returns the map key type for an unwrapped {@code MapKey} annotation type. If the single member
   * type is primitive, returns the boxed type.
   *
   * @throws IllegalArgumentException if {@code mapKeyAnnotationType} is not an annotation type or
   *     has more than one member, or if its single member is an array
   * @throws NoSuchElementException if the annotation has no members
   */
  public static XType getUnwrappedMapKeyType(XType mapKeyAnnotationType) {
    checkArgument(
        isDeclared(mapKeyAnnotationType)
            && mapKeyAnnotationType.getTypeElement().isAnnotationClass(),
        "%s is not an annotation type",
        mapKeyAnnotationType);

    XMethodElement annotationValueMethod =
        getOnlyElement(mapKeyAnnotationType.getTypeElement().getDeclaredMethods());
    XType annotationValueType = annotationValueMethod.getReturnType();
    if (isArray(annotationValueType)) {
      throw new IllegalArgumentException(
          mapKeyAnnotationType
              + "."
              + getSimpleName(annotationValueMethod)
              + " cannot be an array");
    }
    return isPrimitive(annotationValueType) ? annotationValueType.boxed() : annotationValueType;
  }

  /**
   * Returns a code block for {@code binding}'s {@code ContributionBinding#mapKeyAnnotation() map
   * key}. If for whatever reason the map key is not accessible from within {@code requestingClass}
   * (i.e. it has a package-private {@code enum} from a different package), this will return an
   * invocation of a proxy-method giving it access.
   *
   * @throws IllegalStateException if {@code binding} is not a {@code dagger.multibindings.IntoMap
   *     map} contribution.
   */
  public static CodeBlock getMapKeyExpression(
      ContributionBinding binding, ClassName requestingClass, XProcessingEnv processingEnv) {
    AnnotationMirror mapKeyAnnotation = binding.mapKeyAnnotation().get();
    return MapKeyAccessibility.isMapKeyAccessibleFrom(
            mapKeyAnnotation, requestingClass.packageName())
        ? directMapKeyExpression(mapKeyAnnotation, processingEnv)
        : CodeBlock.of("$T.create()", mapKeyProxyClassName(binding));
  }

  /**
   * Returns a code block for the map key annotation {@code mapKey}.
   *
   * <p>This method assumes the map key will be accessible in the context that the returned {@code
   * CodeBlock} is used. Use {@code #getMapKeyExpression(ContributionBinding, ClassName,
   * DaggerElements)} when that assumption is not guaranteed.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one {@code MapKey}
   *     annotation
   * @throws IllegalStateException if {@code bindingElement} is not annotated with a {@code MapKey}
   *     annotation
   */
  private static CodeBlock directMapKeyExpression(
      AnnotationMirror mapKey, XProcessingEnv processingEnv) {
    Optional<? extends AnnotationValue> unwrappedValue = unwrapValue(mapKey);
    AnnotationExpression annotationExpression = new AnnotationExpression(mapKey);

    if (MoreTypes.asTypeElement(mapKey.getAnnotationType())
        .getQualifiedName()
        .contentEquals("dagger.android.AndroidInjectionKey")) {
      XTypeElement unwrappedType =
          DaggerSuperficialValidation.requireTypeElement(
              processingEnv, (String) unwrappedValue.get().getValue());
      return CodeBlock.of(
          "$T.of($S)",
          ClassName.get("dagger.android.internal", "AndroidInjectionKeys"),
          unwrappedType.getClassName().reflectionName());
    }

    if (unwrappedValue.isPresent()) {
      TypeMirror unwrappedValueType =
          getOnlyElement(getAnnotationValuesWithDefaults(mapKey).keySet()).getReturnType();
      return annotationExpression.getValueExpression(unwrappedValueType, unwrappedValue.get());
    } else {
      return annotationExpression.getAnnotationInstanceExpression();
    }
  }

  /**
   * Returns the {@code ClassName} in which {@code #mapKeyFactoryMethod(ContributionBinding,
   * DaggerTypes, DaggerElements)} is generated.
   */
  public static ClassName mapKeyProxyClassName(ContributionBinding binding) {
    return elementBasedClassName(asExecutable(toJavac(binding.bindingElement().get())), "MapKey");
  }

  /**
   * A {@code static create()} method to be added to {@code
   * #mapKeyProxyClassName(ContributionBinding)} when the {@code @MapKey} annotation is not publicly
   * accessible.
   */
  public static Optional<MethodSpec> mapKeyFactoryMethod(
      ContributionBinding binding, XProcessingEnv processingEnv) {
    return binding
        .mapKeyAnnotation()
        .filter(mapKey -> !isMapKeyPubliclyAccessible(mapKey))
        .map(
            mapKey ->
                methodBuilder("create")
                    .addModifiers(PUBLIC, STATIC)
                    .returns(TypeName.get(mapKeyType(toXProcessing(mapKey, processingEnv))))
                    .addStatement("return $L", directMapKeyExpression(mapKey, processingEnv))
                    .build());
  }

  private MapKeys() {}
}
