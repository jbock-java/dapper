package dagger.internal.codegen.xprocessing;

import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;

abstract class JavacExecutableElement extends JavacElement implements XExecutableElement {

  private final ExecutableElement executableElement;
  private final XTypeElement containing;

  JavacExecutableElement(ExecutableElement element, XTypeElement containing, XProcessingEnv env) {
    super(env, element);
    this.executableElement = element;
    this.containing = containing;
  }

  @Override
  public ExecutableElement toJavac() {
    return executableElement;
  }

  @Override
  public XTypeElement getEnclosingElement() {
    return ElementExtKt.getEnclosingType(element, env());
  }

  public List<XType> getThrownTypes() {
    return executableElement.getThrownTypes().stream()
        .map(env()::wrap)
        .collect(Collectors.toList());
  }

  @Override
  public boolean isVarArgs() {
    return executableElement.isVarArgs();
  }

  XTypeElement containing() {
    return containing;
  }

  @Override
  public XMemberContainer getClosestMemberContainer() {
    return getEnclosingElement();
  }
}
