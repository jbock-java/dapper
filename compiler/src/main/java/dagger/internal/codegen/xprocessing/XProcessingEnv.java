package dagger.internal.codegen.xprocessing;

import javax.annotation.processing.ProcessingEnvironment;

public abstract class XProcessingEnv {

  public static XProcessingEnv create(ProcessingEnvironment processingEnv) {
    return new JavacProcessingEnv(processingEnv);
  }
}
