[![dapper](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper/badge.svg?subject=dapper)](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper)

This is a fork of [dagger2](https://github.com/google/dagger) but with a `module-info.java`,
a standard gradle project layout, and without kotlin support.
It was forked from dagger version `2.37`, which is the last dagger
version that did not depend on the "xprocessing" kotlin library.

In order to be modular, dapper uses `jakarta.inject` annotations, instead of `javax.inject`.
