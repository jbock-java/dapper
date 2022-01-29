package dagger.internal.codegen.xprocessing;

import javax.annotation.processing.Messager;

public class XMessager {

  private final Messager messager;

  XMessager(Messager messager) {
    this.messager = messager;
  }

  public Messager toJavac() {
    return messager;
  }
}
