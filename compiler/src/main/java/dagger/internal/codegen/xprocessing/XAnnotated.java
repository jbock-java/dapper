package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.ClassName;
import java.util.List;

public interface XAnnotated {

  List<XAnnotation> getAllAnnotations();

  boolean hasAnnotation(ClassName className);

  XAnnotation getAnnotation(ClassName className);
}
