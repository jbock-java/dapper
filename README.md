[![dapper-compiler](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper-compiler/badge.svg?color=grey&subject=dapper-compiler)](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper-compiler)
[![dapper](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper/badge.svg?subject=dapper)](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper)

Dapper is a fork of [dagger2](https://github.com/google/dagger),
with some modifications:

* gradle project with JUnit 5 tests
* full jpms support with module-name: `dagger`
* removed the multibindings package including `@MapKey`, `@Multibinds`, `@IntoMap` and `@IntoSet`
* removed the producers extension and all producers annotations like `@ProductionComponent`
* remove `@BindsOptional`
* disable members injection and method injection, remove `MembersInjector`
* remove kotlin special-casing
* remove gwt special-casing
* remove guava special-casing
* remove guava dependency
* remove the `experimental_turbine_hjar` option
* no generating `@CanIgnoreReturnValue`
* remove `@Nullable` support

Dapper was forked from dagger version `2.37`, which is the last dagger
version that did not depend on the [xprocessing](https://github.com/google/dagger/issues/2926) kotlin library.

In order to be modular, dapper uses `jakarta.inject` annotations, instead of `javax.inject`.
It also requires Java Version 11 or higher.

For modular applications, add this to `module-info.java`:

````java
requires dagger;
````

For gradle build, add this to `build.gradle`:

````groovy
implementation('io.github.jbock-java:dapper:1.2.1')
annotationProcessor('io.github.jbock-java:dapper-compiler:1.2.1')
````

See also:

* [modular-thermosiphon](https://github.com/jbock-java/modular-thermosiphon) (maven sample)
* [javatests](https://github.com/jbock-java/dapper-javatests) (integration tests)
