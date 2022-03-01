module dagger {

  requires transitive java.compiler;
  requires transitive jakarta.inject;

  exports dagger;
  exports dagger.internal;
  exports dagger.assisted;
  exports dagger.multibindings;
}