[![dapper-compiler](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper-compiler/badge.svg?color=grey&subject=dapper-compiler)](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper-compiler)
[![dapper](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper/badge.svg?subject=dapper)](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper)

Dapper is a fork of [dagger2](https://github.com/google/dagger),
with some modifications:

* gradle project with JUnit 5 tests
* full jpms support with module-name: `dagger`
* removed the producers extension and all producers annotations like `@ProductionComponent`
* remove kotlin special-casing and the dependency on `xprocessing`
* remove gwt special-casing and guava special casing, like support for `c.g.c.b.Optional`
* remove internal guava dependency
* remove the `experimental_turbine_hjar` compiler option

Dapper uses `jakarta.inject` annotations and ignores their `javax.inject` counterparts.
Existing dagger projects can use the following script to replace the imports:

```bash
for F in `find src/main/java -name "*.java"`; do
  for N in Singleton Scope Inject Qualifier Provider; do
    sed -i .bak "s/^import javax.inject.$N;$/import jakarta.inject.$N;/" $F
  done
  rm ${F}.bak
done
```

If you have a modular java build, add this to `module-info.java`:

```java
requires dagger;
```

The gradle config is as follows:

```groovy
implementation('io.github.jbock-java:dapper:2.41.2')
annotationProcessor('io.github.jbock-java:dapper-compiler:2.41.2')
```
