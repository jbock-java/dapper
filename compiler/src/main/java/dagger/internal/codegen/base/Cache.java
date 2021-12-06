package dagger.internal.codegen.base;

import java.util.function.Supplier;

final class Cache<T> {

  private T instance;

  private final Supplier<T> supplier;

  private Cache(Supplier<T> supplier) {
    this.supplier = supplier;
  }

  static <T> Cache<T> of(Supplier<T> supplier) {
    return new Cache<>(supplier);
  }

  T get() {
    if (instance == null) {
      instance = supplier.get();
    }
    return instance;
  }
}
