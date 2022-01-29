package dagger.internal.codegen.xprocessing;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class JavacProcessingEnv extends XProcessingEnv {

  private final ProcessingEnvironment delegate;

  JavacProcessingEnv(ProcessingEnvironment delegate) {
    this.delegate = delegate;
  }

  public Elements elementUtils() {
    return delegate.getElementUtils();
  }

  public Types typeUtils() {
    return delegate.getTypeUtils();
  }
}
