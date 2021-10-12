/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.internal.codegen.kotlin;

import dagger.internal.codegen.base.ClearableCache;


/**
 * Factory creating Kotlin metadata data objects.
 *
 * <p>The metadata is cache since it can be expensive to parse the information stored in a proto
 * binary string format in the metadata annotation values.
 */
@jakarta.inject.Singleton
public final class KotlinMetadataFactory implements ClearableCache {

  @jakarta.inject.Inject
  KotlinMetadataFactory() {
  }

  @Override
  public void clearCache() {
  }
}
