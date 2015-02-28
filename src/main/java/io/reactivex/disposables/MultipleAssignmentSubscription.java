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
import io.reactivex.Observable;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Disposable that can be checked for status such as in a loop inside an {@link Observable} to exit the loop if disposed.
 */
public final class MultipleAssignmentSubscription implements Disposable {

    volatile State state = new State(false, Disposables.empty());
    static final AtomicReferenceFieldUpdater<MultipleAssignmentSubscription, State> STATE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(MultipleAssignmentSubscription.class, State.class, "state");

    private static final class State {
        final boolean isUnsubscribed;
        final Disposable disposable;

        State(boolean u, Disposable s) {
            this.isUnsubscribed = u;
            this.disposable = s;
        }

        State unsubscribe() {
            return new State(true, disposable);
        }

        State set(Disposable s) {
            return new State(isUnsubscribed, s);
        }

    }

    @Override
    public boolean isDisposed() {
        return state.isUnsubscribed;
    }

    @Override
    public void dispose() {
        State oldState;
        State newState;
        do {
            oldState = state;
            if (oldState.isUnsubscribed) {
                return;
            } else {
                newState = oldState.unsubscribe();
            }
        } while (!STATE_UPDATER.compareAndSet(this, oldState, newState));
        oldState.disposable.dispose();
    }

    /**
     * Sets the underlying disposable. If the {@code MultipleAssignmentDisposable} is already disposed,
     * setting a new disposable causes the new disposable to also be immediately disposed.
     *
     * @param s
     *            the {@link Disposable} to set
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
            if (oldState.isUnsubscribed) {
                s.dispose();
                return;
            } else {
                newState = oldState.set(s);
            }
        } while (!STATE_UPDATER.compareAndSet(this, oldState, newState));
    }

    /**
     * Gets the underlying disposable.
     *
     * @return the {@link Disposable} that underlies the {@code MultipleAssignmentDisposable}
     */
    public Disposable get() {
        return state.disposable;
    }

}
