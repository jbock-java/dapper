package dagger.testing.golden;

import io.jbock.testing.compile.JavaFileObjects;

import javax.tools.JavaFileObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class GoldenFile {

  private final Class<?> testClass;
  private final Method testMethod;

  GoldenFile(Class<?> testClass, Method testMethod) {
    this.testClass = testClass;
    this.testMethod = testMethod;
  }

  /**
   * Returns the golden file as a {@link JavaFileObject} containing the file's content.
   *
   * If the golden file does not exist, the returned file object contain an error message pointing
   * to the location of the missing golden file. This can be used with scripting tools to output
   * the correct golden file in the proper location.
   */
  public JavaFileObject get(String qualifiedName, Object... parameters) {
    try {
      return JavaFileObjects.forSourceLines(qualifiedName, goldenFileContent(qualifiedName, parameters));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the golden file content.
   *
   * If the golden file does not exist, the returned content contains an error message pointing
   * to the location of the missing golden file. This can be used with scripting tools to output
   * the correct golden file in the proper location.
   */
  public String goldenFileContent(String qualifiedName, Object... parameters) throws IOException {
    String fileName =
        String.format(
            "%s_%s_%s",
            testClass.getSimpleName(),
            getFormattedMethodName(parameters),
            qualifiedName);
    URL url = testClass.getResource("goldens/" + fileName);
    if (url == null) {
      return "// Error: Missing golden file for goldens/" + fileName;
    }

    return getResourceFileAsString(url);
  }

  static String getResourceFileAsString(URL url) throws IOException {
    try (InputStream is = url.openStream()) {
      if (is == null) return null;
      try (InputStreamReader isr = new InputStreamReader(is);
           BufferedReader reader = new BufferedReader(isr)) {
        return reader.lines().collect(Collectors.joining(System.lineSeparator()));
      }
    }
  }

  /**
   * Returns the formatted method name for the given description.
   *
   * <p>If this is not a parameterized test, we return the method name as is. If it is a
   * parameterized test, we format it from {@code someTestMethod[PARAMETER]} to
   * {@code someTestMethod_PARAMETER} to avoid brackets in the name.
   */
  private String getFormattedMethodName(Object[] parameters) {
//    Matcher matcher = JUNIT_PARAMETERIZED_METHOD.matcher(description.getMethodName());
//
//    // If this is a parameterized method, separate the parameters with an underscore
//    return matcher.find() ? matcher.group(1) + "_" + matcher.group(2) : description.getMethodName();
    String result = testMethod.getName();
    return result + Arrays.stream(parameters).map(Objects::toString).map(s -> "_" + s).collect(Collectors.joining());
  }
}
