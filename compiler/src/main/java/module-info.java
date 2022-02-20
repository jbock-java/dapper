module dagger.compiler {

  uses dagger.spi.BindingGraphPlugin;

  requires dagger;
  requires java.compiler;
  requires java.logging;
  requires io.jbock.common.graph;
  requires io.jbock.auto.common;
  requires io.jbock.javapoet;
  requires io.jbock.auto.value;
}