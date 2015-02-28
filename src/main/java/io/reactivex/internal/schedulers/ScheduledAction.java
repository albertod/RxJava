/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.reactivex.internal.schedulers;

import io.reactivex.Disposable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.exceptions.OnErrorNotImplementedException;
import io.reactivex.functions.Action0;
import io.reactivex.plugins.RxJavaPlugins;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@code Runnable} that executes an {@code Action0} and can be cancelled. The analog is the {@code Subscriber} in respect of an {@code Observer}.
 */
public final class ScheduledAction extends AtomicReference<Thread> implements Runnable, Disposable {
    /** */
    private static final long serialVersionUID = -3962399486978279857L;
    final CompositeDisposable cancel;
    final Action0 action;

    public ScheduledAction(Action0 action) {
        this.action = action;
        this.cancel = new CompositeDisposable();
    }

    @Override
    public void run() {
        try {
            lazySet(Thread.currentThread());
            action.call();
        } catch (Throwable e) {
            // nothing to do but print a System error as this is fatal and there is nowhere else to throw this
            IllegalStateException ie = null;
            if (e instanceof OnErrorNotImplementedException) {
                ie = new IllegalStateException("Exception thrown on Scheduler.Worker thread. Add `onError` handling.", e);
            } else {
                ie = new IllegalStateException("Fatal Exception thrown on Scheduler.Worker thread.", e);
            }
            RxJavaPlugins.getInstance().getErrorHandler().handleError(ie);
            Thread thread = Thread.currentThread();
            thread.getUncaughtExceptionHandler().uncaughtException(thread, ie);
        } finally {
            dispose();
        }
    }

    @Override
    public boolean isDisposed() {
        return cancel.isDisposed();
    }

    @Override
    public void dispose() {
        if (!cancel.isDisposed()) {
            cancel.dispose();
        }
    }

    /**
     * Adds a general Disposable to this {@code ScheduledAction} that will be disposed
     * if the underlying {@code action} completes or the this scheduled action is cancelled.
     *
     * @param s
     *            the Disposable to add
     */
    public void add(Disposable s) {
        cancel.add(s);
    }

    /**
     * Adds the given Future to the Disposable composite in order to support
     * cancelling the underlying task in the executor framework.
     * 
     * @param f
     *            the future to add
     */
    public void add(final Future<?> f) {
        cancel.add(new FutureCompleter(f));
    }

    /**
     * Adds a parent {@link CompositeDisposable} to this {@code ScheduledAction} so when the action is
     * cancelled or terminates, it can remove itself from this parent.
     *
     * @param parent
     *            the parent {@code CompositeDisposable} to add
     */
    public void addParent(CompositeDisposable parent) {
        cancel.add(new Remover(this, parent));
    }

    /**
     * Cancels the captured future if the caller of the call method
     * is not the same as the runner of the outer ScheduledAction to
     * prevent unnecessary self-interrupting if the disposal
     * happens from the same thread.
     */
    private final class FutureCompleter implements Disposable {
        private final Future<?> f;

        private FutureCompleter(Future<?> f) {
            this.f = f;
        }

        @Override
        public void dispose() {
            if (ScheduledAction.this.get() != Thread.currentThread()) {
                f.cancel(true);
            } else {
                f.cancel(false);
            }
        }

        @Override
        public boolean isDisposed() {
            return f.isCancelled();
        }
    }

    /** Remove a child Disposable from a composite when disposing. */
    private static final class Remover extends AtomicBoolean implements Disposable {
        /** */
        private static final long serialVersionUID = 247232374289553518L;
        final Disposable s;
        final CompositeDisposable parent;

        public Remover(Disposable s, CompositeDisposable parent) {
            this.s = s;
            this.parent = parent;
        }

        @Override
        public boolean isDisposed() {
            return s.isDisposed();
        }

        @Override
        public void dispose() {
            if (compareAndSet(false, true)) {
                parent.remove(s);
            }
        }

    }
}
