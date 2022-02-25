package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.ClassName;
import java.util.List;
import java.util.Set;

public interface XAnnotated {

  List<XAnnotation> getAllAnnotations();

  /**
   * Returns the Annotations that are annotated with annotationName
   */
  Set<XAnnotation> getAnnotationsAnnotatedWith(
      ClassName annotationName);


  boolean hasAnnotation(ClassName className);

  /**
   * Returns true if this element has one of the annotations.
   */
  boolean hasAnyAnnotation(ClassName... annotations);

  XAnnotation getAnnotation(ClassName className);
}
