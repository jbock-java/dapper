package dagger.producers.internal;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.internal.Beta;
import jakarta.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

/**
 * TODO remove this class, it will be useless after the next release
 */
@Documented
@Retention(RUNTIME)
@Qualifier
@Beta
public @interface ProductionImplementation {}
