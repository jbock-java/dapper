[![dapper-compiler](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper-compiler/badge.svg?color=grey&subject=dapper-compiler)](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper-compiler)
[![dapper](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper/badge.svg?subject=dapper)](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper)

Dapper is a fork of [dagger2](https://github.com/google/dagger),
with some modifications:

* gradle project with JUnit 5 tests
* full jpms support with module-name: `dagger`
* remove `@BindsOptional`
* removed the producers extension and all producers annotations like `@ProductionComponent`
* remove kotlin special-casing and the dependency on `xprocessing`
* remove gwt special-casing
* remove guava special-casing and internal guava dependency
* remove the `experimental_turbine_hjar` option
* no generating `@CanIgnoreReturnValue`
* ignore `@Nullable` annotations, dapper will never inject `null`

Dapper processes only `jakarta.inject` annotations, but ignores their `javax.inject` counterparts.

For modular applications, add this to `module-info.java`:

````java
requires dagger;
````

For gradle build, add this to `build.gradle`:

````groovy
implementation('io.github.jbock-java:dapper:2.41.1')
annotationProcessor('io.github.jbock-java:dapper-compiler:2.41.1')
````

See also:

* [modular-thermosiphon](https://github.com/jbock-java/modular-thermosiphon) (maven sample)
* [javatests](https://github.com/jbock-java/dapper-javatests) (integration tests)
