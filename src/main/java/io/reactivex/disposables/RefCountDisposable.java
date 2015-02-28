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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Keeps track of the sub-Disposables and disposes the underlying Disposable once all sub-Disposables
 * have been disposed.
 */
public final class RefCountDisposable implements Disposable {
    private final Disposable actual;
    static final State EMPTY_STATE = new State(false, 0);
    volatile State state = EMPTY_STATE;
    static final AtomicReferenceFieldUpdater<RefCountDisposable, State> STATE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(RefCountDisposable.class, State.class, "state");

    private static final class State {
        final boolean isDisposed;
        final int children;

        State(boolean u, int c) {
            this.isDisposed = u;
            this.children = c;
        }

        State addChild() {
            return new State(isDisposed, children + 1);
        }

        State removeChild() {
            return new State(isDisposed, children - 1);
        }

        State dispose() {
            return new State(true, children);
        }

    }

    /**
     * Creates a {@code RefCountDisposable} by wrapping the given non-null {@code Disposable}.
     * 
     * @param s
     *            the {@link Disposable} to wrap
     * @throws IllegalArgumentException
     *             if {@code s} is {@code null}
     */
    public RefCountDisposable(Disposable s) {
        if (s == null) {
            throw new IllegalArgumentException("s");
        }
        this.actual = s;
    }

    /**
     * Returns a new sub-disposable
     *
     * @return a new sub-disposable.
     */
    public Disposable get() {
        State oldState;
        State newState;
        do {
            oldState = state;
            if (oldState.isDisposed) {
                return Disposables.disposed();
            } else {
                newState = oldState.addChild();
            }
        } while (!STATE_UPDATER.compareAndSet(this, oldState, newState));

        return new InnerDisposable(this);
    }

    @Override
    public boolean isDisposed() {
        return state.isDisposed;
    }

    @Override
    public void dispose() {
        State oldState;
        State newState;
        do {
            oldState = state;
            if (oldState.isDisposed) {
                return;
            }
            newState = oldState.dispose();
        } while (!STATE_UPDATER.compareAndSet(this, oldState, newState));
        disposeActualIfApplicable(newState);
    }

    private void disposeActualIfApplicable(State state) {
        if (state.isDisposed && state.children == 0) {
            actual.dispose();
        }
    }

    void disposeAChild() {
        State oldState;
        State newState;
        do {
            oldState = state;
            newState = oldState.removeChild();
        } while (!STATE_UPDATER.compareAndSet(this, oldState, newState));
        disposeActualIfApplicable(newState);
    }

    /** The individual sub-disposables. */
    private static final class InnerDisposable implements Disposable {
        final RefCountDisposable parent;
        volatile int innerDone;
        static final AtomicIntegerFieldUpdater<InnerDisposable> INNER_DONE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(InnerDisposable.class, "innerDone");

        public InnerDisposable(RefCountDisposable parent) {
            this.parent = parent;
        }

        @Override
        public void dispose() {
            if (INNER_DONE_UPDATER.compareAndSet(this, 0, 1)) {
                parent.disposeAChild();
            }
        }

        @Override
        public boolean isDisposed() {
            return innerDone != 0;
        }
    };
}
