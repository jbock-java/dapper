package dagger.internal.codegen.collect;

import java.util.Map;

public interface Table<R, C, V> {

  Map<C, V> row(R rowKey);
}
