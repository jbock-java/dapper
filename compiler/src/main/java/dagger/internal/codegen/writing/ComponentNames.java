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
import static dagger.internal.codegen.extension.DaggerCollectors.onlyElement;
import static java.lang.Character.isUpperCase;
import static java.lang.String.format;

import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.base.Util;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ComponentCreatorDescriptor;
import dagger.internal.codegen.binding.ComponentCreatorKind;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.KeyFactory;
import dagger.model.ComponentPath;
import dagger.spi.model.Key;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;

/**
 * Holds the unique simple names for all components, keyed by their {@link ComponentPath} and
 * {@link Key} of the subcomponent builder.
 */
public final class ComponentNames {
  /** Returns the class name for the root component. */
  public static ClassName getRootComponentClassName(ComponentDescriptor componentDescriptor) {
    Preconditions.checkState(!componentDescriptor.isSubcomponent());
    ClassName componentName = ClassName.get(componentDescriptor.typeElement());
    return ClassName.get(componentName.packageName(), "Dagger" + classFileName(componentName));
  }

  private final ClassName rootName;
  private final Map<ComponentPath, String> namesByPath;
  private final Map<ComponentPath, String> creatorNamesByPath;
  private final Map<Key, List<ComponentPath>> pathsByCreatorKey;

  @Inject
  ComponentNames(@TopLevel BindingGraph graph, KeyFactory keyFactory) {
    this.rootName = getRootComponentClassName(graph.componentDescriptor());
    this.namesByPath = namesByPath(graph);
    this.creatorNamesByPath = creatorNamesByPath(namesByPath, graph);
    this.pathsByCreatorKey = pathsByCreatorKey(keyFactory, graph);
  }

  /** Returns the simple component name for the given {@link ComponentDescriptor}. */
  ClassName get(ComponentPath componentPath) {
    return componentPath.atRoot()
        ? rootName
        : rootName.nestedClass(namesByPath.get(componentPath) + "Impl");
  }

  /**
   * Returns the component descriptor for the component with the given subcomponent creator {@link
   * Key}.
   */
  ClassName getSubcomponentCreatorName(ComponentPath componentPath, Key creatorKey) {
    Preconditions.checkArgument(pathsByCreatorKey.containsKey(creatorKey));
    // First, find the subcomponent path corresponding to the subcomponent creator key.
    // The key may correspond to multiple paths, so we need to find the one under this component.
    ComponentPath subcomponentPath =
        pathsByCreatorKey.getOrDefault(creatorKey, List.of()).stream()
            .filter(path -> path.parent().equals(componentPath))
            .collect(onlyElement());
    return getCreatorName(subcomponentPath);
  }

  /**
   * Returns the simple name for the subcomponent creator implementation for the given {@link
   * ComponentDescriptor}.
   */
  ClassName getCreatorName(ComponentPath componentPath) {
    Preconditions.checkArgument(creatorNamesByPath.containsKey(componentPath));
    return rootName.nestedClass(creatorNamesByPath.get(componentPath));
  }

  private static Map<ComponentPath, String> creatorNamesByPath(
      Map<ComponentPath, String> namesByPath, BindingGraph graph) {
    Map<ComponentPath, String> builder = new LinkedHashMap<>();
    graph
        .componentDescriptorsByPath()
        .forEach(
            (componentPath, componentDescriptor) -> {
              if (componentPath.atRoot()) {
                ComponentCreatorKind creatorKind =
                    componentDescriptor
                        .creatorDescriptor()
                        .map(ComponentCreatorDescriptor::kind)
                        .orElse(ComponentCreatorKind.BUILDER);
                builder.put(componentPath, creatorKind.typeName());
              } else if (componentDescriptor.creatorDescriptor().isPresent()) {
                ComponentCreatorDescriptor creatorDescriptor =
                    componentDescriptor.creatorDescriptor().get();
                String componentName = namesByPath.get(componentPath);
                builder.put(componentPath, componentName + creatorDescriptor.kind().typeName());
              }
            });
    return builder;
  }

  private static Map<ComponentPath, String> namesByPath(BindingGraph graph) {
    Map<ComponentPath, String> componentPathsBySimpleName = new LinkedHashMap<>();
    graph.componentDescriptorsByPath().keySet().stream().collect(
            Collectors.groupingBy(ComponentNames::simpleName, LinkedHashMap::new, Collectors.toList()))
        .values()
        .stream()
        .map(ComponentNames::disambiguateConflictingSimpleNames)
        .forEach(componentPathsBySimpleName::putAll);
    componentPathsBySimpleName.remove(graph.componentPath());
    return componentPathsBySimpleName;
  }

  private static Map<Key, List<ComponentPath>> pathsByCreatorKey(
      KeyFactory keyFactory, BindingGraph graph) {
    Map<Key, List<ComponentPath>> builder = new LinkedHashMap<>();
    graph
        .componentDescriptorsByPath()
        .forEach(
            (componentPath, componentDescriptor) -> {
              if (componentDescriptor.creatorDescriptor().isPresent()) {
                Key creatorKey =
                    keyFactory.forSubcomponentCreator(
                        componentDescriptor.creatorDescriptor().get().typeElement().asType());
                builder.merge(creatorKey, List.of(componentPath), Util::mutableConcat);
              }
            });
    return builder;
  }

  private static Map<ComponentPath, String> disambiguateConflictingSimpleNames(
      Collection<ComponentPath> componentsWithConflictingNames) {
    // If there's only 1 component there's nothing to disambiguate so return the simple name.
    if (componentsWithConflictingNames.size() == 1) {
      ComponentPath componentPath = Util.getOnlyElement(componentsWithConflictingNames);
      return Map.of(componentPath, simpleName(componentPath));
    }

    // There are conflicting simple names, so disambiguate them with a unique prefix.
    // We keep them small to fix https://github.com/google/dagger/issues/421.
    UniqueNameSet nameSet = new UniqueNameSet();
    Map<ComponentPath, String> uniqueNames = new LinkedHashMap<>();
    for (ComponentPath componentPath : componentsWithConflictingNames) {
      String simpleName = simpleName(componentPath);
      String basePrefix = uniquingPrefix(componentPath);
      uniqueNames.put(
          componentPath, format("%s_%s", nameSet.getUniqueName(basePrefix), simpleName));
    }
    return uniqueNames;
  }

  private static String simpleName(ComponentPath componentPath) {
    return componentPath.currentComponent().getSimpleName().toString();
  }

  /** Returns a prefix that could make the component's simple name more unique. */
  private static String uniquingPrefix(ComponentPath componentPath) {
    TypeElement typeElement = componentPath.currentComponent();
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
