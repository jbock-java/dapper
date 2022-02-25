package dagger.internal.codegen.xprocessing;

import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.type.DeclaredType;

public class JavacDeclaredType extends JavacType {

  private final DeclaredType declaredType;

  JavacDeclaredType(XProcessingEnv env, DeclaredType typeMirror) {
    super(env, typeMirror);
    this.declaredType = typeMirror;
  }

  @Override
  public List<XType> getTypeArguments() {
    return declaredType.getTypeArguments().stream()
        .map(typeMirror -> env().wrap(typeMirror))
        .collect(Collectors.toList());
  }

  public DeclaredType toJavac() {
    return declaredType;
  }

  DeclaredType typeMirror() {
    return declaredType;
  }
}
