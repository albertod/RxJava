/**
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
package io.reactivex.disposables;

import io.reactivex.Disposable;
import io.reactivex.annotations.Experimental;
import io.reactivex.functions.Action0;

import java.util.concurrent.Future;

/**
 * Helper methods and utilities for creating and working with {@link Disposable} objects
 */
public final class Disposables {
    private Disposables() {
        throw new IllegalStateException("No instances!");
    }

    /**
     * Returns a {@link Disposable} to which {@code dispose} does nothing except to change {@code isUnsubscribed} to {@code true}. It's stateful and {@code isUnsubscribed} indicates if
     * {@code dispose} is called, which is different from {@link #disposed()}.
     *
     * <pre><code>
     * Disposable empty = Disposables.empty();
     * System.out.println(empty.isUnsubscribed()); // false
     * empty.dispose();
     * System.out.println(empty.isUnsubscribed()); // true
     * </code></pre>
     *
     * @return a {@link Disposable} to which {@code dispose} does nothing except to change {@code isUnsubscribed} to {@code true}
     */
    public static Disposable empty() {
        return BooleanDisposable.create();
    }

    /**
     * Returns a {@link Disposable} to which {@code dispose} does nothing, as it is already disposed.
     * Its {@code isUnsubscribed} always returns {@code true}, which is different from {@link #empty()}.
     *
     * <pre><code>
     * Disposable disposed = Disposables.disposed();
     * System.out.println(disposed.isUnsubscribed()); // true
     * </code></pre>
     *
     * @return a {@link Disposable} to which {@code dispose} does nothing, as it is already disposed
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public static Disposable disposed() {
        return DISPOSED;
    }

    /**
     * Creates and returns a {@link Disposable} that invokes the given {@link Action0} when disposed.
     * 
     * @param dispose
     *            Action to invoke on dispose.
     * @return {@link Disposable}
     */
    public static Disposable create(final Action0 dispose) {
        return BooleanDisposable.create(dispose);
    }

    /**
     * Converts a {@link Future} into a {@link Disposable} and cancels it when disposed.
     * 
     * @param f
     *            the {@link Future} to convert
     * @return a {@link Disposable} that wraps {@code f}
     */
    public static Disposable from(final Future<?> f) {
        return new FutureSubscription(f);
    }

    /** Naming classes helps with debugging. */
    private static final class FutureSubscription implements Disposable {
        final Future<?> f;

        public FutureSubscription(Future<?> f) {
            this.f = f;
        }

        @Override
        public void dispose() {
            f.cancel(true);
        }

        @Override
        public boolean isDisposed() {
            return f.isCancelled();
        }
    }

    /**
     * Converts a set of {@link Disposable}s into a {@link CompositeDisposable} that groups the multiple
     * Disposables together and unsubscribes from all of them together.
     * 
     * @param disposables
     *            the Disposables to group together
     * @return a {@link CompositeDisposable} representing the {@code disposables} set
     */

    public static CompositeDisposable from(Disposable... disposables) {
        return new CompositeDisposable(disposables);
    }

    /**
     * A {@link Disposable} that does nothing when its dispose method is called.
     */
    private static final Disposed DISPOSED = new Disposed();

    /** Naming classes helps with debugging. */
    private static final class Disposed implements Disposable {
        @Override
        public void dispose() {
        }

        @Override
        public boolean isDisposed() {
            return true;
        }
    }
}
