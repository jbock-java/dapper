package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.ClassName;
import java.util.List;

public interface XAnnotated {

  List<XAnnotation> getAllAnnotations();

  boolean hasAnnotation(ClassName className);

  /**
   * Returns true if this element has one of the annotations.
   */
  boolean hasAnyAnnotation(ClassName... annotations);

  XAnnotation getAnnotation(ClassName className);
}
