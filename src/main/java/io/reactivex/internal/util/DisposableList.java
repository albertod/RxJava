/**
 * Copyright 2014 Netflix, Inc.
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
package io.reactivex.internal.util;

import io.reactivex.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.exceptions.Exceptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.reactivestreams.Subscription;

/**
 * Subscription that represents a group of Subscriptions that are unsubscribed together.
 * 
 * @see <a href="http://msdn.microsoft.com/en-us/library/system.reactive.disposables.compositedisposable(v=vs.103).aspx">Rx.Net equivalent CompositeDisposable</a>
 */
public final class DisposableList implements Disposable {

    private List<Disposable> disposables;
    private volatile boolean isDisposed;

    public DisposableList() {
    }

    public DisposableList(final Disposable... disposables) {
        this.disposables = new LinkedList<>(Arrays.asList(disposables));
    }

    @Override
    public boolean isDisposed() {
        return isDisposed;
    }

    /**
     * Adds a new {@link Disposable} to this {@code DisposableList} if the {@code DisposableList} is
     * not yet disposed. If the {@code DisposableList} <em>is</em> disposed, {@code add} will
     * indicate this by explicitly disposing the new {@code Disposable} as well.
     *
     * @param s
     *            the {@link Disposable} to add
     */
    public void add(final Disposable s) {
        if (!isDisposed) {
            synchronized (this) {
                if (!isDisposed) {
                    if (disposables == null) {
                        disposables = new LinkedList<>();
                    }
                    disposables.add(s);
                    return;
                }
            }
        }
        // call after leaving the synchronized block so we're not holding a lock while executing this
        s.dispose();
    }

    public void add(Subscription s) {
        add(Disposables.create(() -> s.cancel()));
    }

    /**
     * Dispose all of the subscriptions in the list, which stops the receipt of notifications on
     * the associated {@code Disposable}.
     */
    @Override
    public void dispose() {
        if (!isDisposed) {
            List<Disposable> list;
            synchronized (this) {
                if (isDisposed) {
                    return;
                }
                isDisposed = true;
                list = disposables;
                disposables = null;
            }
            // we will only get here once
            disposeAll(list);
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

    /* perf support */
    public void clear() {
        if (!isDisposed) {
            List<Disposable> list;
            synchronized (this) {
                list = disposables;
                disposables = null;
            }
            disposeAll(list);
        }
    }

    /**
     * Returns true if this composite is not disposed and contains Disposables.
     * 
     * @return {@code true} if this composite is not disposed and contains Disposables.
     */
    public boolean hasDisposables() {
        if (!isDisposed) {
            synchronized (this) {
                return !isDisposed && disposables != null && !disposables.isEmpty();
            }
        }
        return false;
    }

}
