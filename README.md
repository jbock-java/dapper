[![dapper-compiler](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper-compiler/badge.svg?color=grey&subject=dapper-compiler)](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper-compiler)
[![dapper](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper/badge.svg?subject=dapper)](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper)

Dapper is a fork of [dagger2](https://github.com/google/dagger),
with some modifications:

* full jpms support with module-name `dagger`
* removed the `unwrapValue` attribute from the `MapKey` annotation, it is now effectively always `true`
* removed the producers extension and all producers annotations like `@ProductionComponent`
* removed some kotlin-related special-casing

Dapper was forked from dagger version `2.37`, which is the last dagger
version that did not depend on the [xprocessing](https://github.com/google/dagger/issues/2926) kotlin library.

In order to be modular, dapper uses `jakarta.inject` annotations, instead of `javax.inject`.
It also requires Java Version 11 or higher.

Add to `module-info.java`:

````java
requires dagger;
````

Gradle users add this to `build.gradle`:

````groovy
implementation('io.github.jbock-java:dapper:1.2')
annotationProcessor('io.github.jbock-java:dapper-compiler:1.2')
````

For maven users, there is the [modular-thermosiphon](https://github.com/jbock-java/modular-thermosiphon) sample project.

Some of the integration tests from the [javatests](https://github.com/google/dagger/tree/master/javatests) directory could not
be run as part of the regular build. Their compilation requires the result of the actual annotation processing.
I could only make this work with a released artifact, not with a gradle inter-module dependency.
This is why [dagger-javatests](https://github.com/jbock-java/dapper-javatests) was created.
A new release should be tested there first, via staging repo.
