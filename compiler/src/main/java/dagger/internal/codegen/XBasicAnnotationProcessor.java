package dagger.internal.codegen;

import dagger.internal.codegen.xprocessing.XProcessingEnv;

public interface XBasicAnnotationProcessor {

  XProcessingEnv getXProcessingEnv();

  void initialize(XProcessingEnv processingEnv);
}
