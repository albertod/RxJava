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
import io.reactivex.functions.Action0;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Disposable that can be checked for status such as in a loop inside an {@link Observable} to exit the loop if disposed.
 */
public final class BooleanDisposable implements Disposable {

    private final Action0 action;
    volatile int disposed;
    static final AtomicIntegerFieldUpdater<BooleanDisposable> DISPOSED_UPDATER = AtomicIntegerFieldUpdater.newUpdater(BooleanDisposable.class, "disposed");

    public BooleanDisposable() {
        action = null;
    }

    private BooleanDisposable(Action0 action) {
        this.action = action;
    }

    /**
     * Creates a {@code BooleanDisposable} without dispose behavior.
     *
     * @return the created {@code BooleanDisposable}
     */
    public static BooleanDisposable create() {
        return new BooleanDisposable();
    }

    /**
     * Creates a {@code BooleanDisposable} with a specified function to invoke upon dispose.
     *
     * @param onDispose
     *            an {@link Action0} to invoke upon dispose
     * @return the created {@code BooleanDisposable}
     */
    public static BooleanDisposable create(Action0 onDispose) {
        return new BooleanDisposable(onDispose);
    }

    @Override
    public boolean isDisposed() {
        return disposed != 0;
    }

    @Override
    public final void dispose() {
        if (DISPOSED_UPDATER.compareAndSet(this, 0, 1)) {
            if (action != null) {
                action.call();
            }
        }
    }

}
