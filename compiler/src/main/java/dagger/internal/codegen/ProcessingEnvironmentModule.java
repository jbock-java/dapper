/*
 * Copyright (C) 2018 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.Reusable;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.compileroption.ProcessingEnvironmentCompilerOptions;
import dagger.internal.codegen.compileroption.ProcessingOptions;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.xprocessing.XConverters;
import dagger.internal.codegen.xprocessing.XFiler;
import dagger.internal.codegen.xprocessing.XMessager;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.multibindings.IntoSet;
import jakarta.inject.Singleton;
import java.util.Map;
import javax.lang.model.SourceVersion;

/** Bindings that depend on the {@code XProcessingEnv}. */
@Module
interface ProcessingEnvironmentModule {
  @Binds
  @Reusable // to avoid parsing options more than once
  CompilerOptions bindCompilerOptions(
      ProcessingEnvironmentCompilerOptions processingEnvironmentCompilerOptions);

  @Provides
  @ProcessingOptions
  static Map<String, String> processingOptions(XProcessingEnv xProcessingEnv) {
    return xProcessingEnv.getOptions();
  }

  @Provides
  static XMessager messager(XProcessingEnv xProcessingEnv) {
    return xProcessingEnv.getMessager();
  }

  @Provides
  static XFiler filer(CompilerOptions compilerOptions, XProcessingEnv xProcessingEnv) {
    return xProcessingEnv.getFiler();
  }

  @Provides
  static SourceVersion sourceVersion(XProcessingEnv xProcessingEnv) {
    return XConverters.toJavac(xProcessingEnv).getSourceVersion();
  }

  @Provides
  @Singleton
  static DaggerElements daggerElements(XProcessingEnv xProcessingEnv) {
    return new DaggerElements(
        XConverters.toJavac(xProcessingEnv).getElementUtils(),
        XConverters.toJavac(xProcessingEnv).getTypeUtils());
  }

  @Provides
  @Singleton
  static DaggerTypes daggerTypes(XProcessingEnv xProcessingEnv, DaggerElements elements) {
    return new DaggerTypes(XConverters.toJavac(xProcessingEnv).getTypeUtils(), elements);
  }

  @Binds
  @IntoSet
  ClearableCache daggerElementAsClearableCache(DaggerElements elements);
}
