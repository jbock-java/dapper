package dagger.internal.codegen.xprocessing;

import java.util.List;
import javax.lang.model.element.AnnotationMirror;

public interface XAnnotation {

  List<XAnnotationValue> getAnnotationValues();

  XType getType();

  AnnotationMirror toJavac();
}
