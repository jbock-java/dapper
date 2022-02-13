package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.JavaFile;
import javax.annotation.processing.Filer;

public interface XFiler {

  void write(JavaFile javaFile);

  Filer toJavac();
}
