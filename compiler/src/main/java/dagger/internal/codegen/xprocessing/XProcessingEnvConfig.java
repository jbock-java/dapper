package dagger.internal.codegen.xprocessing;

public class XProcessingEnvConfig {

  public static class Builder {
    public Builder disableAnnotatedElementValidation(boolean b) {
      return this;
    }

    public XProcessingEnvConfig build() {
      return new XProcessingEnvConfig();
    }
  }
}

