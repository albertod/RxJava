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
package io.reactivex.subscribers;

import io.reactivex.Notification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A {@code TestSubscriber} is a variety of {@link Subscriber} that you can use for unit testing, to perform
 * assertions, inspect received events, or wrap a mocked {@code Subscriber}.
 */
public class TestSubscriber<T> implements Subscriber<T> {

    private final Subscriber<T> delegate;
    private final ArrayList<T> onNextEvents = new ArrayList<T>();
    private final ArrayList<Throwable> onErrorEvents = new ArrayList<Throwable>();
    private final ArrayList<Notification<T>> onCompletedEvents = new ArrayList<Notification<T>>();

    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile Thread lastSeenThread;

    public TestSubscriber(Subscriber<T> delegate) {
        this.delegate = delegate;
    }

    public TestSubscriber() {
        this.delegate = new Subscriber<T>() {

            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T t) {

            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onComplete() {

            }

        };
    }

    private Subscription subscription = null;

    @Override
    public void onSubscribe(Subscription s) {
        if (this.subscription != null) {
            throw new IllegalStateException("This instance can only be used once.");
        }
        this.subscription = s;
        this.delegate.onSubscribe(s);
    }

    /**
     * Notifies the Subscriber that the {@code Observable} has finished sending push-based notifications.
     * <p>
     * The {@code Observable} will not call this method if it calls {@link #onError}.
     */
    @Override
    public void onComplete() {
        try {
            lastSeenThread = Thread.currentThread();
            onCompletedEvents.add(Notification.createOnComplete());
            delegate.onComplete();
        } finally {
            latch.countDown();
        }
    }

    /**
     * Get the {@link Notification}s representing each time this {@link Subscriber} was notified of sequence
     * completion via {@link #onCompleted}, as a {@link List}.
     *
     * @return a list of Notifications representing calls to this Subscriber's {@link #onCompleted} method
     */
    public List<Notification<T>> getOnCompletedEvents() {
        return onCompletedEvents;
    }

    /**
     * Notifies the Subscriber that the {@code Observable} has experienced an error condition.
     * <p>
     * If the {@code Observable} calls this method, it will not thereafter call {@link #onNext} or {@link #onCompleted}.
     * 
     * @param e
     *            the exception encountered by the Observable
     */
    @Override
    public void onError(Throwable e) {
        try {
            lastSeenThread = Thread.currentThread();
            onErrorEvents.add(e);
            delegate.onError(e);
        } finally {
            latch.countDown();
        }
    }

    /**
     * Get the {@link Throwable}s this {@link Subscriber} was notified of via {@link #onError} as a {@link List}.
     *
     * @return a list of the Throwables that were passed to this Subscriber's {@link #onError} method
     */
    public List<Throwable> getOnErrorEvents() {
        return onErrorEvents;
    }

    /**
     * Provides the Subscriber with a new item to observe.
     * <p>
     * The {@code Observable} may call this method 0 or more times.
     * <p>
     * The {@code Observable} will not call this method again after it calls either {@link #onCompleted} or {@link #onError}.
     * 
     * @param t
     *            the item emitted by the Observable
     */
    @Override
    public void onNext(T t) {
        lastSeenThread = Thread.currentThread();
        onNextEvents.add(t);
        delegate.onNext(t);
    }

    /**
     * Allow calling the protected {@link #request(long)} from unit tests.
     *
     * @param n
     *            the maximum number of items you want the Observable to emit to the Subscriber at this time, or {@code Long.MAX_VALUE} if you want the Observable to emit items at its own pace
     */
    public void requestMore(long n) {
        subscription.request(n);
    }

    /**
     * Get the sequence of items observed by this {@link Subscriber}, as an ordered {@link List}.
     *
     * @return a list of items observed by this Subscriber, in the order in which they were observed
     */
    public List<T> getOnNextEvents() {
        return onNextEvents;
    }

    /**
     * Get a list containing all of the items and notifications received by this observer, where the items
     * will be given as-is, any error notifications will be represented by their {@code Throwable}s, and any
     * sequence-complete notifications will be represented by their {@code Notification} objects.
     *
     * @return a {@link List} containing one item for each item or notification received by this observer, in
     *         the order in which they were observed or received
     */
    public List<Object> getEvents() {
        ArrayList<Object> events = new ArrayList<Object>();
        events.add(onNextEvents);
        events.add(onErrorEvents);
        events.add(onCompletedEvents);
        return Collections.unmodifiableList(events);
    }

    /**
     * Assert that a particular sequence of items was received in order.
     *
     * @param items
     *            the sequence of items expected to have been observed
     * @throws AssertionError
     *             if the sequence of items observed does not exactly match {@code items}
     */
    public void assertReceivedOnNext(List<T> items) {
        if (onNextEvents.size() != items.size()) {
            throw new AssertionError("Number of items does not match. Provided: " + items.size() + "  Actual: " + onNextEvents.size());
        }

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == null) {
                // check for null equality
                if (onNextEvents.get(i) != null) {
                    throw new AssertionError("Value at index: " + i + " expected to be [null] but was: [" + onNextEvents.get(i) + "]");
                }
            } else if (!items.get(i).equals(onNextEvents.get(i))) {
                throw new AssertionError("Value at index: " + i + " expected to be [" + items.get(i) + "] (" + items.get(i).getClass().getSimpleName() + ") but was: [" + onNextEvents.get(i) + "] (" + onNextEvents.get(i).getClass().getSimpleName() + ")");

            }
        }

    }

    /**
     * Awaits terminal events, asserts no errors and asserts onNext. 
     * <p>
     * Shortcut for the following:
     * <pre>{@code 
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertReceivedOnNext(items);
     * }</pre>
     * 
     * @param items
     */
    public void assertSuccessfulOnNext(List<T> items) {
        awaitTerminalEvent();
        assertNoErrors();
        assertReceivedOnNext(items);
    }
    
    /**
     * Awaits terminal events, asserts no errors and asserts onNext. 
     * <p>
     * Shortcut for the following:
     * <pre>{@code 
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertReceivedOnNext(items);
     * }</pre>
     * 
     * @param items
     */
    public void assertSuccessfulOnNextOf(T... items) {
        awaitTerminalEvent();
        assertNoErrors();
        assertReceivedOnNext(Arrays.asList(items));
    }
    
    /**
     * Assert that a single terminal event occurred, either {@link #onCompleted} or {@link #onError}.
     *
     * @throws AssertionError
     *             if not exactly one terminal event notification was received
     */
    public void assertTerminalEvent() {
        if (onErrorEvents.size() > 1) {
            throw new AssertionError("Too many onError events: " + onErrorEvents.size());
        }

        if (onCompletedEvents.size() > 1) {
            throw new AssertionError("Too many onCompleted events: " + onCompletedEvents.size());
        }

        if (onCompletedEvents.size() == 1 && onErrorEvents.size() == 1) {
            throw new AssertionError("Received both an onError and onCompleted. Should be one or the other.");
        }

        if (onCompletedEvents.size() == 0 && onErrorEvents.size() == 0) {
            throw new AssertionError("No terminal events received.");
        }
    }

    /**
     * Assert that this {@code Subscriber} has received no {@code onError} notifications.
     * 
     * @throws AssertionError
     *             if this {@code Subscriber} has received one or more {@code onError} notifications
     */
    public void assertNoErrors() {
        if (getOnErrorEvents().size() > 0) {
            // can't use AssertionError because (message, cause) doesn't exist until Java 7
            throw new RuntimeException("Unexpected onError events: " + getOnErrorEvents().size(), getOnErrorEvents().get(0));
            // TODO possibly check for Java7+ and then use AssertionError at runtime (since we always compile with 7)
        }
    }

    /**
     * Blocks until this {@link Subscriber} receives a notification that the {@code Observable} is complete
     * (either an {@code onCompleted} or {@code onError} notification).
     *
     * @throws RuntimeException
     *             if the Subscriber is interrupted before the Observable is able to complete
     */
    public void awaitTerminalEvent() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
    }

    /**
     * Blocks until this {@link Subscriber} receives a notification that the {@code Observable} is complete
     * (either an {@code onCompleted} or {@code onError} notification), or until a timeout expires.
     *
     * @param timeout
     *            the duration of the timeout
     * @param unit
     *            the units in which {@code timeout} is expressed
     * @throws RuntimeException
     *             if the Subscriber is interrupted before the Observable is able to complete
     */
    public void awaitTerminalEvent(long timeout, TimeUnit unit) {
        try {
            latch.await(timeout, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
    }

    /**
     * Blocks until this {@link Subscriber} receives a notification that the {@code Observable} is complete
     * (either an {@code onCompleted} or {@code onError} notification), or until a timeout expires; if the
     * Subscriber is interrupted before either of these events take place, this method unsubscribes the
     * Subscriber from the Observable).
     *
     * @param timeout
     *            the duration of the timeout
     * @param unit
     *            the units in which {@code timeout} is expressed
     */
    public void awaitTerminalEventAndUnsubscribeOnTimeout(long timeout, TimeUnit unit) {
        try {
            awaitTerminalEvent(timeout, unit);
        } catch (RuntimeException e) {
            if (subscription == null) {
                throw new RuntimeException("Exception occurred while waiting and no subscription to cancel", e);
            } else {
                subscription.cancel();
            }
        }
    }

    /**
     * Returns the last thread that was in use when an item or notification was received by this {@link Subscriber}.
     *
     * @return the {@code Thread} on which this Subscriber last received an item or notification from the
     *         Observable it is subscribed to
     */
    public Thread getLastSeenThread() {
        return lastSeenThread;
    }
}
