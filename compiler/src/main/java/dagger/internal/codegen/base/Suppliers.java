package dagger.internal.codegen.base;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class Suppliers {

  private Suppliers() {
  }

  public static <T> com.google.common.base.Supplier<T> memoize(Supplier<T> delegate) {
    return Cache.of(delegate);
  }

  public static IntSupplier memoizeInt(IntSupplier delegate) {
    return new IntCache(delegate);
  }

  private static final class Cache<T> implements com.google.common.base.Supplier<T> {

    private boolean hit;
    private T instance;

    private final Supplier<T> supplier;

    Cache(Supplier<T> supplier) {
      this.supplier = supplier;
    }

    static <T> Cache<T> of(Supplier<T> supplier) {
      return new Cache<>(supplier);
    }

    @Override
    public T get() {
      if (!hit) {
        hit = true;
        instance = supplier.get();
      }
      return instance;
    }
  }

  private static final class IntCache implements IntSupplier {

    private boolean hit;
    private int cached;

    private final IntSupplier supplier;

    IntCache(IntSupplier supplier) {
      this.supplier = supplier;
    }

    @Override
    public int getAsInt() {
      if (!hit) {
        hit = true;
        cached = supplier.getAsInt();
      }
      return cached;
    }
  }
}
