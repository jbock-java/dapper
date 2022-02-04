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

import static dagger.internal.codegen.base.ElementFormatter.elementToString;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;
import static javax.tools.Diagnostic.Kind.WARNING;

import dagger.internal.codegen.xprocessing.XAnnotation;
import dagger.internal.codegen.xprocessing.XAnnotationValue;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XMessager;
import io.jbock.common.graph.Traverser;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;

/** A collection of issues to report for source code. */
public final class ValidationReport {
  private static final Traverser<ValidationReport> SUBREPORTS =
      Traverser.forTree(report -> report.subreports);

  private final Element subject;
  private final Set<Item> items;
  private final Set<ValidationReport> subreports;
  private final boolean markedDirty;
  private boolean hasPrintedErrors;

  private ValidationReport(
      Element subject,
      Set<Item> items,
      Set<ValidationReport> subreports,
      boolean markedDirty) {
    this.subject = subject;
    this.items = items;
    this.subreports = subreports;
    this.markedDirty = markedDirty;
  }

  /** Returns the items from this report and all transitive subreports. */
  public Set<Item> allItems() {
    return StreamSupport.stream(SUBREPORTS.depthFirstPreOrder(this).spliterator(), false)
        .flatMap(report -> report.items.stream())
        .collect(toImmutableSet());
  }

  /**
   * Returns {@code true} if there are no errors in this report or any subreports and markedDirty is
   * {@code false}.
   */
  public boolean isClean() {
    if (markedDirty) {
      return false;
    }
    for (Item item : items) {
      switch (item.kind()) {
        case ERROR:
          return false;
        default:
          break;
      }
    }
    for (ValidationReport subreport : subreports) {
      if (!subreport.isClean()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Prints all messages to {@code messager} (and recurs for subreports). If a message's {@linkplain
   * Item#element() element} is contained within the report's subject, associates the message with
   * the message's element. Otherwise, since {@link Diagnostic} reporting is expected to be
   * associated with elements that are currently being compiled, associates the message with the
   * subject itself and prepends a reference to the item's element.
   */
  public void printMessagesTo(XMessager messager) {
    printMessagesTo(messager.toJavac());
  }

  /**
   * Prints all messages to {@code messager} (and recurs for subreports). If a
   * message's {@linkplain Item#element() element} is contained within the report's subject,
   * associates the message with the message's element. Otherwise, since {@link Diagnostic}
   * reporting is expected to be associated with elements that are currently being compiled,
   * associates the message with the subject itself and prepends a reference to the item's element.
   */
  public void printMessagesTo(Messager messager) {
    if (hasPrintedErrors) {
      // Avoid printing the errors from this validation report more than once.
      return;
    }
    hasPrintedErrors = true;
    for (Item item : items) {
      if (isEnclosedIn(subject, item.element())) {
        if (item.annotation().isPresent()) {
          if (item.annotationValue().isPresent()) {
            messager.printMessage(
                item.kind(),
                item.message(),
                item.element(),
                item.annotation().get(),
                item.annotationValue().get());
          } else {
            messager.printMessage(
                item.kind(), item.message(), item.element(), item.annotation().get());
          }
        } else {
          messager.printMessage(item.kind(), item.message(), item.element());
        }
      } else {
        String message = String.format("[%s] %s", elementToString(item.element()), item.message());
        messager.printMessage(item.kind(), message, subject);
      }
    }
    for (ValidationReport subreport : subreports) {
      subreport.printMessagesTo(messager);
    }
  }

  private static boolean isEnclosedIn(Element parent, Element child) {
    Element current = child;
    while (current != null) {
      if (current.equals(parent)) {
        return true;
      }
      current = current.getEnclosingElement();
    }
    return false;
  }

  /** Metadata about a {@link ValidationReport} item. */
  public static final class Item {
    private final String message;
    private final Kind kind;
    private final Element element;
    private final Optional<AnnotationMirror> annotation;
    private final Optional<AnnotationValue> annotationValue;

    public Item(
        String message,
        Kind kind,
        Element element,
        Optional<AnnotationMirror> annotation,
        Optional<AnnotationValue> annotationValue) {
      this.message = message;
      this.kind = kind;
      this.element = element;
      this.annotation = annotation;
      this.annotationValue = annotationValue;
    }

    public String message() {
      return message;
    }

    public Kind kind() {
      return kind;
    }

    public Element element() {
      return element;
    }

    public Optional<AnnotationMirror> annotation() {
      return annotation;
    }

    public Optional<AnnotationValue> annotationValue() {
      return annotationValue;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != this.getClass()) return false;
      var that = (Item) obj;
      return Objects.equals(this.message, that.message) &&
          Objects.equals(this.kind, that.kind) &&
          Objects.equals(this.element, that.element) &&
          Objects.equals(this.annotation, that.annotation) &&
          Objects.equals(this.annotationValue, that.annotationValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(message, kind, element, annotation, annotationValue);
    }

    @Override
    public String toString() {
      return "Item[" +
          "message=" + message + ", " +
          "kind=" + kind + ", " +
          "element=" + element + ", " +
          "annotation=" + annotation + ", " +
          "annotationValue=" + annotationValue + ']';
    }
  }

  public static Builder about(Element subject) {
    return new Builder(subject);
  }

  public static Builder about(XElement subject) {
    return new Builder(subject.toJavac());
  }

  /** A {@link ValidationReport} builder. */
  public static final class Builder {
    private final Element subject;
    private final Set<Item> items = new LinkedHashSet<>();
    private final Set<ValidationReport> subreports = new LinkedHashSet<>();
    private boolean markedDirty;

    private Builder(Element subject) {
      this.subject = subject;
    }

    Element getSubject() {
      return subject;
    }

    Builder addItems(Iterable<Item> newItems) {
      newItems.forEach(items::add);
      return this;
    }

    public Builder addError(String message) {
      return addError(message, subject);
    }

    public Builder addError(String message, Element element) {
      return addItem(message, ERROR, element);
    }

    public Builder addError(String message, Element element, AnnotationMirror annotation) {
      return addItem(message, ERROR, element, annotation);
    }

    public Builder addError(
        String message,
        Element element,
        AnnotationMirror annotation,
        AnnotationValue annotationValue) {
      return addItem(message, ERROR, element, annotation, annotationValue);
    }

    public Builder addError(String message, XElement element) {
      return addItem(message, ERROR, element);
    }

    public Builder addError(String message, XElement element, XAnnotation annotation) {
      return addItem(message, ERROR, element, annotation);
    }

    public Builder addError(
        String message,
        XElement element,
        XAnnotation annotation,
        XAnnotationValue annotationValue) {
      return addItem(message, ERROR, element, annotation, annotationValue);
    }

    Builder addWarning(String message) {
      return addWarning(message, subject);
    }

    Builder addWarning(String message, Element element) {
      return addItem(message, WARNING, element);
    }

    Builder addWarning(String message, Element element, AnnotationMirror annotation) {
      return addItem(message, WARNING, element, annotation);
    }

    Builder addWarning(
        String message,
        Element element,
        AnnotationMirror annotation,
        AnnotationValue annotationValue) {
      return addItem(message, WARNING, element, annotation, annotationValue);
    }

    Builder addWarning(String message, XElement element) {
      return addItem(message, WARNING, element);
    }

    Builder addWarning(String message, XElement element, XAnnotation annotation) {
      return addItem(message, WARNING, element, annotation);
    }

    Builder addWarning(
        String message,
        XElement element,
        XAnnotation annotation,
        XAnnotationValue annotationValue) {
      return addItem(message, WARNING, element, annotation, annotationValue);
    }

    Builder addNote(String message) {
      return addNote(message, subject);
    }

    Builder addNote(String message, Element element) {
      return addItem(message, NOTE, element);
    }

    Builder addNote(String message, Element element, AnnotationMirror annotation) {
      return addItem(message, NOTE, element, annotation);
    }

    Builder addNote(
        String message,
        Element element,
        AnnotationMirror annotation,
        AnnotationValue annotationValue) {
      return addItem(message, NOTE, element, annotation, annotationValue);
    }

    Builder addNote(String message, XElement element) {
      return addItem(message, NOTE, element);
    }

    Builder addNote(String message, XElement element, XAnnotation annotation) {
      return addItem(message, NOTE, element, annotation);
    }

    Builder addNote(
        String message,
        XElement element,
        XAnnotation annotation,
        XAnnotationValue annotationValue) {
      return addItem(message, NOTE, element, annotation, annotationValue);
    }

    Builder addItem(String message, Kind kind, Element element) {
      return addItem(message, kind, element, Optional.empty(), Optional.empty());
    }

    Builder addItem(String message, Kind kind, Element element, AnnotationMirror annotation) {
      return addItem(message, kind, element, Optional.of(annotation), Optional.empty());
    }

    Builder addItem(
        String message,
        Kind kind,
        Element element,
        AnnotationMirror annotation,
        AnnotationValue annotationValue) {
      return addItem(message, kind, element, Optional.of(annotation), Optional.of(annotationValue));
    }

    private Builder addItem(
        String message,
        Kind kind,
        Element element,
        Optional<AnnotationMirror> annotation,
        Optional<AnnotationValue> annotationValue) {
      items.add(
          new Item(message, kind, element, annotation, annotationValue));
      return this;
    }

    Builder addItem(String message, Kind kind, XElement element) {
      return addItem(message, kind, element, Optional.empty(), Optional.empty());
    }

    Builder addItem(String message, Kind kind, XElement element, XAnnotation annotation) {
      return addItem(message, kind, element, Optional.of(annotation), Optional.empty());
    }

    Builder addItem(
        String message,
        Kind kind,
        XElement element,
        XAnnotation annotation,
        XAnnotationValue annotationValue) {
      return addItem(message, kind, element, Optional.of(annotation), Optional.of(annotationValue));
    }

    private Builder addItem(
        String message,
        Kind kind,
        XElement element,
        Optional<XAnnotation> annotation,
        Optional<XAnnotationValue> annotationValue) {
      items.add(
          new Item(
              message,
              kind,
              element.toJavac(),
              annotation.map(XAnnotation::toJavac),
              annotationValue.map(XAnnotationValue::toJavac)));
      return this;
    }

    /**
     * If called, then {@link #isClean()} will return {@code false} even if there are no error items
     * in the report.
     */
    void markDirty() {
      this.markedDirty = true;
    }

    public Builder addSubreport(ValidationReport subreport) {
      subreports.add(subreport);
      return this;
    }

    public ValidationReport build() {
      return new ValidationReport(subject, items, subreports, markedDirty);
    }
  }
}
