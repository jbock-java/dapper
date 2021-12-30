/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.producers.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.Futures.catchingAsync;
import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Produced;
import dagger.producers.Producer;
import jakarta.inject.Provider;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility methods for use in generated producer code.
 */
public final class Producers {
  private static final Function<Object, Produced<Object>> RESULT_TO_PRODUCED =
      new Function<Object, Produced<Object>>() {
        @Override
        public Produced<Object> apply(Object result) {
          return Produced.successful(result);
        }
      };

  private static final AsyncFunction<Throwable, Produced<Object>> FUTURE_FALLBACK_FOR_PRODUCED =
      new AsyncFunction<Throwable, Produced<Object>>() {
        @Override
        public ListenableFuture<Produced<Object>> apply(Throwable t) throws Exception {
          Produced<Object> produced = Produced.failed(t);
          return Futures.immediateFuture(produced);
        }
      };

  /**
   * Returns a new view of the given {@code producer} for use as an entry point in a production
   * component, if and only if it is a {@link CancellableProducer}. When the returned producer's
   * future is cancelled, the given {@code cancellable} will also be cancelled.
   *
   * @throws IllegalArgumentException if {@code producer} is not a {@code CancellableProducer}
   */
  public static <T> Producer<T> entryPointViewOf(
      Producer<T> producer, CancellationListener cancellationListener) {
    // This is a hack until we change the types of Producer fields to be CancellableProducer or
    // some other type.
    if (producer instanceof CancellableProducer) {
      return ((CancellableProducer<T>) producer).newEntryPointView(cancellationListener);
    }
    throw new IllegalArgumentException(
        "entryPointViewOf called with non-CancellableProducer: " + producer);
  }

  /**
   * Calls {@code cancel} on the given {@code producer} if it is a {@link CancellableProducer}.
   *
   * @throws IllegalArgumentException if {@code producer} is not a {@code CancellableProducer}
   */
  public static void cancel(Producer<?> producer, boolean mayInterruptIfRunning) {
    // This is a hack until we change the types of Producer fields to be CancellableProducer or
    // some other type.
    if (producer instanceof CancellableProducer) {
      ((CancellableProducer<?>) producer).cancel(mayInterruptIfRunning);
    } else {
      throw new IllegalArgumentException("cancel called with non-CancellableProducer: " + producer);
    }
  }

  /**
   * A {@link CancellableProducer} which can't be cancelled because it represents an
   * already-completed task.
   */
  private abstract static class CompletedProducer<T> implements CancellableProducer<T> {
    @Override
    public void cancel(boolean mayInterruptIfRunning) {
    }

    @Override
    public Producer<T> newDependencyView() {
      return this;
    }

    @Override
    public Producer<T> newEntryPointView(CancellationListener cancellationListener) {
      return this;
    }
  }

  private Producers() {
  }
}
