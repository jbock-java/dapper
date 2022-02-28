package dagger.internal.codegen.xprocessing;

import dagger.internal.codegen.base.Preconditions;
import dagger.internal.codegen.base.Suppliers;
import dagger.internal.codegen.extension.DaggerStreams;
import io.jbock.auto.common.MoreElements;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

class JavacRoundEnv implements XRoundEnv {

  private final JavacProcessingEnv env;
  private final RoundEnvironment delegate;
  private final Supplier<Set<XElement>> rootElementsCache;

  JavacRoundEnv(JavacProcessingEnv env, RoundEnvironment delegate) {
    this.env = env;
    this.delegate = delegate;
    this.rootElementsCache =
        Suppliers.memoize(
            () ->
                delegate.getRootElements().stream()
                    .map(
                        it -> {
                          Preconditions.checkState(MoreElements.isType(it));
                          return env.wrapTypeElement(MoreElements.asType(it));
                        })
                    .collect(DaggerStreams.toImmutableSet()));
  }

  @Override
  public Set<XElement> getRootElements() {
    return rootElementsCache.get();
  }

  @Override
  public boolean isProcessingOver() {
    return delegate.processingOver();
  }

  @Override
  public Set<XElement> getElementsAnnotatedWith(String annotationQualifiedName) {
    if (annotationQualifiedName.equals("*")) {
      return Set.of();
    }
    TypeElement annotationTypeElement =
        env.getElementUtils().getTypeElement(annotationQualifiedName);
    if (annotationTypeElement == null) {
      return Set.of();
    }
    Set<Element> elements = (Set<Element>) delegate.getElementsAnnotatedWith(annotationTypeElement);
    return wrapAnnotatedElements(elements, annotationQualifiedName);
  }

  @Override
  public RoundEnvironment toJavac() {
    return delegate;
  }

  private Set<XElement> wrapAnnotatedElements(Set<Element> elements, String annotationName) {
    return elements.stream()
        .map(it -> env.wrapAnnotatedElement(it, annotationName))
        .collect(DaggerStreams.toImmutableSet());
  }
}
