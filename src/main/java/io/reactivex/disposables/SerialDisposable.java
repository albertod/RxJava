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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Represents a disposable whose underlying disposable can be swapped for another disposable which causes
 * the previous underlying disposable to be disposed.
 */
public final class SerialDisposable implements Disposable {
    volatile State state = new State(false, Disposables.empty());
    static final AtomicReferenceFieldUpdater<SerialDisposable, State> STATE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(SerialDisposable.class, State.class, "state");

    private static final class State {
        final boolean isDisposed;
        final Disposable disposable;

        State(boolean u, Disposable s) {
            this.isDisposed = u;
            this.disposable = s;
        }

        State dispose() {
            return new State(true, disposable);
        }

        State set(Disposable s) {
            return new State(isDisposed, s);
        }

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
            } else {
                newState = oldState.dispose();
            }
        } while (!STATE_UPDATER.compareAndSet(this, oldState, newState));
        oldState.disposable.dispose();
    }

    /**
     * Swaps out the old {@link Disposable} for the specified {@code Disposable}.
     *
     * @param s
     *            the new {@code Disposable} to swap in
     * @throws IllegalArgumentException
     *             if {@code s} is {@code null}
     */
    public void set(Disposable s) {
        if (s == null) {
            throw new IllegalArgumentException("Disposable can not be null");
        }
        State oldState;
        State newState;
        do {
            oldState = state;
            if (oldState.isDisposed) {
                s.dispose();
                return;
            } else {
                newState = oldState.set(s);
            }
        } while (!STATE_UPDATER.compareAndSet(this, oldState, newState));
        oldState.disposable.dispose();
    }

    /**
     * Retrieves the current {@link Disposable} that is being represented by this {@code SerialDisposable}.
     * 
     * @return the current {@link Disposable} that is being represented by this {@code SerialDisposable}
     */
    public Disposable get() {
        return state.disposable;
    }

}
