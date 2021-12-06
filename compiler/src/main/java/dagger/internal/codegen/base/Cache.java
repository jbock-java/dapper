package dagger.internal.codegen.base;

import java.util.function.Supplier;

public final class Cache<T> {

  private boolean hit;
  private T instance;

  private final Supplier<T> supplier;

  private Cache(Supplier<T> supplier) {
    this.supplier = supplier;
  }

  public static <T> Cache<T> of(Supplier<T> supplier) {
    return new Cache<>(supplier);
  }

  public T get() {
    if (!hit) {
      hit = true;
      instance = supplier.get();
    }
    return instance;
  }
}
