package dagger.internal.codegen.base;

import dagger.internal.codegen.xprocessing.XElement;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

public class MapKeys {

  public static Optional<AnnotationMirror> getMapKey(XElement bindingElement) {
    return Optional.empty();
  }

  public static Optional<AnnotationMirror> getMapKey(Element bindingElement) {
    return Optional.empty();
  }
}
