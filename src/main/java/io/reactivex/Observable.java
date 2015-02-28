/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */
package io.reactivex;

import io.reactivex.exceptions.OnErrorNotImplementedException;
import io.reactivex.functions.Action0;
import io.reactivex.functions.Action1;

import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * The Observable class that implements the Reactive Streams Publisher.
 * <p>
 * This class provides methods for subscribing to the Observable as well as delegate methods to the various Observers.
 * <p>
 * The documentation for this class makes use of marble diagrams. The following legend explains these diagrams:
 * <p>
 * <img width="640" height="301" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/legend.png" alt="">
 * <p>
 * For more information see the <a href="http://reactivex.io/documentation/observable.html">ReactiveX documentation</a>
 * and <a href="http://reactive-streams.org">Reactive Streams spec</a>.
 * 
 * @param <T>
 *            the type of the items emitted by the Observable
 */
public class Observable<T> implements Publisher<T> {

    private OnSubscribe<T> onSubscribe;

    /**
     * Creates an Observable with a Function to execute when it is subscribed to.
     * <p>
     * <em>Note:</em> Use {@link #create(OnSubscribe)} to create an Observable, instead of this constructor,
     * unless you specifically have a need for inheritance.
     * 
     * @param f
     *            {@link OnSubscribe} to be executed when {@link #subscribe(Subscriber)} is called
     */
    protected Observable(OnSubscribe<T> f) {
        this.onSubscribe = f;
    }

    /**
     * Returns an Observable that will execute the specified function when a {@link Subscriber} subscribes to
     * it.
     * <p>
     * <img width="640" height="200" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/create.png" alt="">
     * <p>
     * Write the function you pass to {@code create} so that it behaves as an Observable: It should invoke the
     * Subscriber's {@link Subscriber#onNext onNext}, {@link Subscriber#onError onError}, and {@link Subscriber#onCompleted onCompleted} methods appropriately.
     * <p>
     * A well-formed Observable must invoke either the Subscriber's {@code onCompleted} method exactly once or
     * its {@code onError} method exactly once.
     * <p>
     * See <a href="http://go.microsoft.com/fwlink/?LinkID=205219">Rx Design Guidelines (PDF)</a> for detailed
     * information.
     * <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>{@code create} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T>
     *            the type of the items that this Observable emits
     * @param f
     *            a function that accepts an {@code Subscriber<T>}, and invokes its {@code onNext}, {@code onError}, and {@code onCompleted} methods as appropriate
     * @return an Observable that, when a {@link Subscriber} subscribes to it, will execute the specified
     *         function
     * @see <a href="http://reactivex.io/documentation/operators/create.html">ReactiveX operators documentation: Create</a>
     */
    public final static <T> Observable<T> create(OnSubscribe<T> f) {
        return new Observable<T>(f);
    }

    /**
     * Invoked when Obserable.subscribe is called.
     */
    public static interface OnSubscribe<T> extends Action1<Subscriber<? super T>> {
        // cover for generics insanity
    }

    /**
     * Operator function for lifting into an Observable.
     */
    public interface Operator<R, T> extends Function<Subscriber<? super R>, Subscriber<? super T>> {
        // cover for generics insanity
    }

    /**
     * Lifts a function to the current Observable and returns a new Observable that when subscribed to will pass
     * the values of the current Observable through the Operator function.
     * <p>
     * In other words, this allows chaining Observers together on an Observable for acting on the values within
     * the Observable.
     * <p> {@code
     * observable.map(...).filter(...).take(5).lift(new OperatorA()).lift(new OperatorB(...)).subscribe()
     * } <p>
     * If the operator you are creating is designed to act on the individual items emitted by a source
     * Observable, use {@code lift}. If your operator is designed to transform the source Observable as a whole
     * (for instance, by applying a particular set of existing RxJava operators to it) use {@link #compose}.
     * <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>{@code lift} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param lift
     *            the Operator that implements the Observable-operating function to be applied to the source
     *            Observable
     * @return an Observable that is the result of applying the lifted Operator to the source Observable
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Implementing-Your-Own-Operators">RxJava wiki: Implementing Your Own Operators</a>
     */
    public final <R> Observable<R> lift(final Operator<? extends R, ? super T> lift) {
        return new Observable<R>(new OnSubscribe<R>() {
            @Override
            public void call(Subscriber<? super R> o) {
                try {
                    Subscriber<? super T> st = lift.apply(o);
                    try {
                        onSubscribe.call(st);
                    } catch (Throwable e) {
                        st.onError(e);
                    }
                } catch (Throwable e) {
                    o.onError(e);
                }
            }
        });
    }

    /**
     * Subscribes to the {@link Observable} and receives notifications for each element.
     * <p>
     * Alias to {@link #subscribe(Action1)} <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>{@code forEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            {@link Action1} to execute for each item.
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null, or
     *             if {@code onComplete} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final void forEach(final Action1<? super T> onNext) {
        subscribe(onNext);
    }

    /**
     * Subscribes to the {@link Observable} and receives notifications for each element and error events.
     * <p>
     * Alias to {@link #subscribe(Action1, Action1)} <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>{@code forEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            {@link Action1} to execute for each item.
     * @param onError
     *            {@link Action1} to execute when an error is emitted.
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null, or
     *             if {@code onComplete} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final void forEach(final Action1<? super T> onNext, final Action1<Throwable> onError) {
        subscribe(onNext, onError);
    }

    /**
     * Subscribes to the {@link Observable} and receives notifications for each element and the terminal events.
     * <p>
     * Alias to {@link #subscribe(Action1, Action1, Action0)} <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>{@code forEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            {@link Action1} to execute for each item.
     * @param onError
     *            {@link Action1} to execute when an error is emitted.
     * @param onComplete
     *            {@link Action0} to execute when completion is signalled.
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null, or
     *             if {@code onComplete} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final void forEach(final Action1<? super T> onNext, final Action1<Throwable> onError, final Action0 onComplete) {
        subscribe(onNext, onError, onComplete);
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        onSubscribe.call(s);
    }

    public void subscribe(Observer<? super T> o) {
        subscribe(new Subscriber<T>() {

            @Override
            public void onSubscribe(Subscription s) {
                // default behavior for an Observer that is not participating in backpressure
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T t) {
                o.onNext(t);
            }

            @Override
            public void onError(Throwable t) {
                o.onError(t);
            }

            @Override
            public void onComplete() {
                o.onComplete();
            }

        });
    }

    /**
     * Subscribes to an Observable but ignore its emissions and notifications.
     * <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @throws OnErrorNotImplementedException
     *             if the Observable tries to call {@code onError}
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final void subscribe() {
        subscribe(new Subscriber<T>() {

            @Override
            public final void onComplete() {
                // do nothing
            }

            @Override
            public final void onError(Throwable e) {
                throw new OnErrorNotImplementedException(e);
            }

            @Override
            public final void onNext(T args) {
                // do nothing
            }

            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

        });
    }

    /**
     * Subscribes to an Observable and provides a callback to handle the items it emits.
     * <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            the {@code Action1<T>} you have designed to accept emissions from the Observable
     * @throws IllegalArgumentException
     *             if {@code onNext} is null
     * @throws OnErrorNotImplementedException
     *             if the Observable tries to call {@code onError}
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final void subscribe(final Action1<? super T> onNext) {
        if (onNext == null) {
            throw new IllegalArgumentException("onNext can not be null");
        }

        subscribe(new Subscriber<T>() {

            @Override
            public final void onComplete() {
                // do nothing
            }

            @Override
            public final void onError(Throwable e) {
                throw new OnErrorNotImplementedException(e);
            }

            @Override
            public final void onNext(T args) {
                onNext.call(args);
            }

            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

        });
    }

    /**
     * Subscribes to an Observable and provides callbacks to handle the items it emits and any error
     * notification it issues.
     * <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            the {@code Action1<T>} you have designed to accept emissions from the Observable
     * @param onError
     *            the {@code Action1<Throwable>} you have designed to accept any error notification from the
     *            Observable
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null
     */
    public final void subscribe(final Action1<? super T> onNext, final Action1<Throwable> onError) {
        if (onNext == null) {
            throw new IllegalArgumentException("onNext can not be null");
        }
        if (onError == null) {
            throw new IllegalArgumentException("onError can not be null");
        }

        subscribe(new Subscriber<T>() {

            @Override
            public final void onComplete() {
                // do nothing
            }

            @Override
            public final void onError(Throwable e) {
                onError.call(e);
            }

            @Override
            public final void onNext(T args) {
                onNext.call(args);
            }

            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

        });
    }

    /**
     * Subscribes to an Observable and provides callbacks to handle the items it emits and any error or
     * completion notification it issues.
     * <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            the {@code Action1<T>} you have designed to accept emissions from the Observable
     * @param onError
     *            the {@code Action1<Throwable>} you have designed to accept any error notification from the
     *            Observable
     * @param onComplete
     *            the {@code Action0} you have designed to accept a completion notification from the
     *            Observable
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items before
     *         the Observable has finished sending them
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null, or
     *             if {@code onComplete} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final void subscribe(final Action1<? super T> onNext, final Action1<Throwable> onError, final Action0 onComplete) {
        if (onNext == null) {
            throw new IllegalArgumentException("onNext can not be null");
        }
        if (onError == null) {
            throw new IllegalArgumentException("onError can not be null");
        }
        if (onComplete == null) {
            throw new IllegalArgumentException("onComplete can not be null");
        }

        subscribe(new Subscriber<T>() {

            @Override
            public final void onComplete() {
                onComplete.call();
            }

            @Override
            public final void onError(Throwable e) {
                onError.call(e);
            }

            @Override
            public final void onNext(T args) {
                onNext.call(args);
            }

            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }
        });
    }

}
