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

import static dagger.internal.codegen.binding.SourceFiles.classFileName;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static java.lang.Character.isUpperCase;
import static java.lang.String.format;

import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ComponentCreatorDescriptor;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.KeyFactory;
import dagger.model.Key;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;

/**
 * Holds the unique simple names for all components, keyed by their {@link ComponentDescriptor} and
 * {@link Key} of the subcomponent builder.
 */
public final class ComponentNames {
  /** Returns the class name for the root component. */
  public static ClassName getRootComponentClassName(ComponentDescriptor componentDescriptor) {
    Preconditions.checkState(!componentDescriptor.isSubcomponent());
    ClassName componentName = ClassName.get(componentDescriptor.typeElement());
    return ClassName.get(componentName.packageName(), "Dagger" + classFileName(componentName));
  }

  private final Map<ComponentDescriptor, String> namesByDescriptor;
  private final Map<Key, ComponentDescriptor> descriptorsByCreatorKey;

  @Inject
  ComponentNames(@TopLevel BindingGraph graph, KeyFactory keyFactory) {
    this.namesByDescriptor = namesByDescriptor(graph);
    this.descriptorsByCreatorKey = descriptorsByCreatorKey(keyFactory, namesByDescriptor.keySet());
  }

  /** Returns the simple component name for the given {@link ComponentDescriptor}. */
  String get(ComponentDescriptor componentDescriptor) {
    return componentDescriptor.isSubcomponent()
        ? namesByDescriptor.get(componentDescriptor)
        : getRootComponentClassName(componentDescriptor).simpleName();
  }

  /**
   * Returns the simple name for the subcomponent creator implementation with the given {@link Key}.
   */
  String getCreatorName(Key key) {
    return getCreatorName(descriptorsByCreatorKey.get(key));
  }

  /**
   * Returns the simple name for the subcomponent creator implementation for the given {@link
   * ComponentDescriptor}.
   */
  String getCreatorName(ComponentDescriptor componentDescriptor) {
    Preconditions.checkArgument(componentDescriptor.creatorDescriptor().isPresent());
    ComponentCreatorDescriptor creatorDescriptor = componentDescriptor.creatorDescriptor().get();
    return get(componentDescriptor) + creatorDescriptor.kind().typeName();
  }

  private static Map<ComponentDescriptor, String> namesByDescriptor(BindingGraph graph) {
    Map<String, List<ComponentDescriptor>> componentDescriptorsBySimpleName =
        graph.componentDescriptors().stream().collect(Collectors.groupingBy(ComponentNames::simpleName, LinkedHashMap::new, Collectors.toList()));
    Map<ComponentDescriptor, String> subcomponentImplSimpleNames = new LinkedHashMap<>();
    componentDescriptorsBySimpleName.values().stream()
        .map(ComponentNames::disambiguateConflictingSimpleNames)
        .forEach(subcomponentImplSimpleNames::putAll);
    subcomponentImplSimpleNames.remove(graph.componentDescriptor());
    return subcomponentImplSimpleNames;
  }

  private static Map<Key, ComponentDescriptor> descriptorsByCreatorKey(
      KeyFactory keyFactory, Set<ComponentDescriptor> subcomponents) {
    return subcomponents.stream()
        .filter(subcomponent -> subcomponent.creatorDescriptor().isPresent())
        .collect(
            toImmutableMap(
                subcomponent ->
                    keyFactory.forSubcomponentCreator(
                        subcomponent.creatorDescriptor().orElseThrow().typeElement().asType()),
                subcomponent -> subcomponent));
  }

  private static Map<ComponentDescriptor, String> disambiguateConflictingSimpleNames(
      Collection<ComponentDescriptor> componentsWithConflictingNames) {
    // If there's only 1 component there's nothing to disambiguate so return the simple name.
    if (componentsWithConflictingNames.size() == 1) {
      ComponentDescriptor component = Util.getOnlyElement(componentsWithConflictingNames);
      return Map.of(component, simpleName(component));
    }

    // There are conflicting simple names, so disambiguate them with a unique prefix.
    // We keep them small to fix https://github.com/google/dagger/issues/421.
    UniqueNameSet nameSet = new UniqueNameSet();
    Map<ComponentDescriptor, String> uniqueNames = new LinkedHashMap<>();
    for (ComponentDescriptor component : componentsWithConflictingNames) {
      String simpleName = simpleName(component);
      String basePrefix = uniquingPrefix(component);
      uniqueNames.put(component, format("%s_%s", nameSet.getUniqueName(basePrefix), simpleName));
    }
    return uniqueNames;
  }

  private static String simpleName(ComponentDescriptor component) {
    return component.typeElement().getSimpleName().toString();
  }

  /** Returns a prefix that could make the component's simple name more unique. */
  private static String uniquingPrefix(ComponentDescriptor component) {
    TypeElement typeElement = component.typeElement();
    String containerName = typeElement.getEnclosingElement().getSimpleName().toString();

    // If parent element looks like a class, use its initials as a prefix.
    if (!containerName.isEmpty() && isUpperCase(containerName.charAt(0))) {
      return containerName.replaceAll("[a-z]", "");
    }

    // Not in a normally named class. Prefix with the initials of the elements leading here.
    Name qualifiedName = typeElement.getQualifiedName();
    Iterator<String> pieces = Arrays.asList(qualifiedName.toString().split("[.]", -1)).iterator();
    StringBuilder b = new StringBuilder();

    while (pieces.hasNext()) {
      String next = pieces.next();
      if (pieces.hasNext()) {
        b.append(next.charAt(0));
      }
    }

    // Note that a top level class in the root package will be prefixed "$_".
    return b.length() > 0 ? b.toString() : "$";
  }
}
