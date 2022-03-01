package dagger.internal.codegen;

import java.util.Arrays;
import java.util.List;

public class JavaFileObjectos {

  public static JavaFileBuilder forSourceLines(String... qualifiedName) {
    List<String> lines = Arrays.asList(qualifiedName);
    return CompilerMode.DEFAULT_MODE
        .javaFileBuilder(lines.get(0))
        .addLines(lines.subList(1, lines.size()).toArray(new String[0]));
  }
}
