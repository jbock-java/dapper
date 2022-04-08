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

import static dagger.internal.codegen.base.Preconditions.checkNotNull;

import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;

/**
 * Represents a {@code com.sun.source.tree.MemberSelectTree} as a {@code CodeBlock}.
 */
abstract class MemberSelect {

  /**
   * Returns a {@code MemberSelect} that accesses the field given by {@code fieldName} owned by
   * {@code owningClass}. In this context "local" refers to the fact that the field is owned by the
   * type (or an enclosing type) from which the code block will be used. The returned {@code
   * MemberSelect} will not be valid for accessing the field from a different class (regardless of
   * accessibility).
   */
  static MemberSelect localField(ShardImplementation owningShard, String fieldName) {
    return new LocalField(owningShard.name(), owningShard.shardFieldReference(), fieldName);
  }

  /**
   * Returns a {@code MemberSelect} that accesses the field given by {@code fieldName} owned by
   * {@code owningClass}. In this context "local" refers to the fact that the field is owned by the
   * type (or an enclosing type) from which the code block will be used. The returned {@code
   * MemberSelect} will not be valid for accessing the field from a different class (regardless of
   * accessibility).
   */
  static MemberSelect localField(ComponentImplementation owningComponent, String fieldName) {
    return new LocalField(
        owningComponent.name(), owningComponent.componentFieldReference(), fieldName);
  }

  private static final class LocalField extends MemberSelect {
    final CodeBlock owningFieldReference;
    final String fieldName;

    LocalField(ClassName owningClassName, CodeBlock owningFieldReference, String fieldName) {
      super(owningClassName, false);
      this.owningFieldReference = owningFieldReference;
      this.fieldName = checkNotNull(fieldName);
    }

    @Override
    CodeBlock getExpressionFor(ClassName usingClass) {
      return owningClass().equals(usingClass)
          ? CodeBlock.of("$N", fieldName)
          : CodeBlock.of("$L.$N", owningFieldReference, fieldName);
    }
  }

  private final ClassName owningClass;
  private final boolean staticMember;

  MemberSelect(ClassName owningClass, boolean staticMemeber) {
    this.owningClass = owningClass;
    this.staticMember = staticMemeber;
  }

  /** Returns the class that owns the member being selected. */
  ClassName owningClass() {
    return owningClass;
  }

  /**
   * Returns true if the member being selected is static and does not require an instance of
   * {@code #owningClass()}.
   */
  boolean staticMember() {
    return staticMember;
  }

  /**
   * Returns a {@code CodeBlock} suitable for accessing the member from the given {@code
   * usingClass}.
   */
  abstract CodeBlock getExpressionFor(ClassName usingClass);
}
