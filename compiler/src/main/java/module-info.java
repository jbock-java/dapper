module dagger.compiler {

  uses dagger.spi.BindingGraphPlugin;

  requires dagger;
  requires java.compiler;
  requires java.logging;
  requires com.google.common.graph;
  requires auto.common;
  requires com.squareup.javapoet;
}