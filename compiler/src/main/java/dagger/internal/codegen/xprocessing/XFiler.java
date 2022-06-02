package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.JavaFile;
import javax.annotation.processing.Filer;

public interface XFiler {

  void write(JavaFile javaFile);

  default void write(JavaFile javaFile, XFiler.Mode mode) {
    write(javaFile);
  }

  Filer toJavac();

  /**
   * Specifies whether a file represents aggregating or isolating inputs for incremental
   * build purposes. This does not apply in Javac processing because aggregating vs isolating
   * is set on the processor level. For more on KSP's definitions of isolating vs aggregating
   * see the documentation at
   * https://github.com/google/ksp/blob/master/docs/incremental.md
   */
  enum Mode {
    Aggregating, Isolating
  }
}
