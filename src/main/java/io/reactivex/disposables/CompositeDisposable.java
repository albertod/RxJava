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
import io.reactivex.exceptions.Exceptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Disposable that represents a group of Disposables that are disposed together.
 */
public final class CompositeDisposable implements Disposable {

    private Set<Disposable> disposables;
    private volatile boolean disposed;

    public CompositeDisposable() {
    }

    public CompositeDisposable(final Disposable... disposables) {
        this.disposables = new HashSet<>(Arrays.asList(disposables));
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Adds a new {@link Subscription} to this {@code CompositeSubscription} if the {@code CompositeSubscription} is not yet disposed. If the {@code CompositeSubscription} <em>is</em>
     * disposed, {@code add} will indicate this by explicitly unsubscribing the new {@code Subscription} as
     * well.
     *
     * @param s
     *            the {@link Subscription} to add
     */
    public void add(final Disposable s) {
        if (s.isDisposed()) {
            return;
        }
        if (!disposed) {
            synchronized (this) {
                if (!disposed) {
                    if (disposables == null) {
                        disposables = new HashSet<>(4);
                    }
                    disposables.add(s);
                    return;
                }
            }
        }
        // call after leaving the synchronized block so we're not holding a lock while executing this
        s.dispose();
    }

    /**
     * Removes a {@link Subscription} from this {@code CompositeSubscription}, and unsubscribes the {@link Subscription}.
     *
     * @param s
     *            the {@link Subscription} to remove
     */
    public void remove(final Disposable s) {
        if (!disposed) {
            boolean dispose = false;
            synchronized (this) {
                if (disposed || disposables == null) {
                    return;
                }
                dispose = disposables.remove(s);
            }
            if (dispose) {
                // if we removed successfully we then need to call unsubscribe on it (outside of the lock)
                s.dispose();
            }
        }
    }

    /**
     * Unsubscribes any disposables that are currently part of this {@code CompositeSubscription} and remove
     * them from the {@code CompositeSubscription} so that the {@code CompositeSubscription} is empty and in
     * an unoperative state.
     */
    public void clear() {
        if (!disposed) {
            Collection<Disposable> dispose = null;
            synchronized (this) {
                if (disposed || disposables == null) {
                    return;
                } else {
                    dispose = disposables;
                    disposables = null;
                }
            }
            disposeAll(dispose);
        }
    }

    @Override
    public void dispose() {
        if (!disposed) {
            Collection<Disposable> unsubscribe = null;
            synchronized (this) {
                if (disposed) {
                    return;
                }
                disposed = true;
                unsubscribe = disposables;
                disposables = null;
            }
            // we will only get here once
            disposeAll(unsubscribe);
        }
    }

    private static void disposeAll(Collection<Disposable> disposables) {
        if (disposables == null) {
            return;
        }
        List<Throwable> es = null;
        for (Disposable s : disposables) {
            try {
                s.dispose();
            } catch (Throwable e) {
                if (es == null) {
                    es = new ArrayList<Throwable>();
                }
                es.add(e);
            }
        }
        Exceptions.throwIfAny(es);
    }

    /**
     * Returns true if this composite is not disposed and contains disposables.
     *
     * @return {@code true} if this composite is not disposed and contains disposables.
     */
    public boolean hasDisposables() {
        if (!disposed) {
            synchronized (this) {
                return !disposed && disposables != null && !disposables.isEmpty();
            }
        }
        return false;
    }
}
