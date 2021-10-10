package dagger.internal.codegen;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.COMPONENT_FACTORY;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("test issue #1")
public class JakartaTest extends ComponentCreatorTestHelper {

  public JakartaTest() {
    super(DEFAULT_MODE, COMPONENT_FACTORY);
  }

  @Test
  public void testEmptyCreator() {
    JavaFileObject injectableTypeFile =
        JavaFileObjects.forSourceLines(
            "test.SomeInjectableType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class SomeInjectableType {",
            "  @Inject SomeInjectableType() {}",
            "}");
    JavaFileObject componentFile =
        preprocessedJavaFile(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component",
            "interface SimpleComponent {",
            "  SomeInjectableType someInjectableType();",
            "",
            "  @Component.Builder",
            "  static interface Builder {",
            "     SimpleComponent build();",
            "  }",
            "}");
    JavaFileObject generatedComponent =
        preprocessedJavaFile(
            "test.DaggerSimpleComponent",
            "package test;",
            "",
            GeneratedLines.generatedAnnotations(),
            "final class DaggerSimpleComponent implements SimpleComponent {",
            "  private static final class Builder implements SimpleComponent.Builder {",
            "    @Override",
            "    public SimpleComponent build() {",
            "      return new DaggerSimpleComponent();",
            "    }",
            "  }",
            "}");
    Compilation compilation = compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsElementsIn(generatedComponent);
  }
}
