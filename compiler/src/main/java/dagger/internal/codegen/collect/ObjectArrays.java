package dagger.internal.codegen.collect;

import java.util.Arrays;

public class ObjectArrays {

  public static <T> T[] concat(T[] array, T element) {
    T[] result = Arrays.copyOf(array, array.length + 1);
    result[array.length] = element;
    return result;
  }
}
