package dagger.internal.codegen.collect;

public class HashMultimap<X, Y> extends SetMultimap<X, Y> {

  public static <X, Y> HashMultimap<X, Y> create() {
    return new HashMultimap<>();
  }
}
