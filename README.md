[![dapper-compiler](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper-compiler/badge.svg?color=grey&subject=dapper-compiler)](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper-compiler)
[![dapper](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper/badge.svg?subject=dapper)](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper)

This is a fork of [dagger2](https://github.com/google/dagger) but with a `module-info.java`,
a standard gradle project layout, and without kotlin support.
It was forked from dagger version `2.37`, which is the last dagger
version that did not depend on the "xprocessing" kotlin library.

In order to be modular, dapper uses `jakarta.inject` annotations, instead of `javax.inject`.
It also requires Java 9.

Add to `module-info.java`:

````java
requires jakarta.inject;
requires dagger;
````

Gradle users add this to `build.gradle`:

````groovy
implementation('io.github.jbock-java:dapper:1.0')
annotationProcessor('io.github.jbock-java:dapper-compiler:1.0')
````

For maven users, there is the [modular-thermosiphon](https://github.com/h908714124/modular-thermosiphon) sample project.

