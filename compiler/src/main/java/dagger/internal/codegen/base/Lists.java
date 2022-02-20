package dagger.internal.codegen.base;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Lists {

  public static <E> List<List<E>> partition(List<E> list, int size) {
    if (list.size() <= size) {
      return List.of(list);
    }
    List<List<E>> result = new ArrayList<>();
    List<E> current = null;
    for (int i = 0; i < list.size(); i++) {
      if (i % size == 0) {
        result.add(current = new ArrayList<>(size));
      }
      current.add(list.get(i));
    }
    return result;
  }

  public static <X, Y> List<Y> transform(List<X> input, Function<X, Y> f) {
    return input.stream().map(f).collect(Collectors.toList());
  }

  private Lists() {
  }
}
