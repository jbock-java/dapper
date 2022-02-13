package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.JavaFile;
import java.io.IOException;
import javax.annotation.processing.Filer;

public class JavacFiler implements XFiler {

  private final XProcessingEnv processingEnv;
  private final Filer delegate;

  public JavacFiler(XProcessingEnv processingEnv, Filer delegate) {
    this.processingEnv = processingEnv;
    this.delegate = delegate;
  }

  @Override
  public void write(JavaFile javaFile) {
    try {
      javaFile.writeTo(delegate);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Filer toJavac() {
    return delegate;
  }
}
