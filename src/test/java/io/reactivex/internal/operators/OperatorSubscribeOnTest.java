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
package io.reactivex.internal.operators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.reactivex.Disposable;
import io.reactivex.Observable;
import io.reactivex.Observable.OnSubscribe;
import io.reactivex.Observable.Operator;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.functions.Action0;
import io.reactivex.internal.util.DisposableList;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class OperatorSubscribeOnTest {

    @Test(timeout = 2000)
    public void testIssue813() throws InterruptedException {
        // https://github.com/ReactiveX/RxJava/issues/813
        final CountDownLatch scheduled = new CountDownLatch(1);
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(1);

        TestSubscriber<Integer> observer = new TestSubscriber<Integer>();

        Observable
                .create(new Observable.OnSubscribe<Integer>() {
                    @Override
                    public void call(
                            final Subscriber<? super Integer> subscriber) {
                        scheduled.countDown();
                        try {
                            try {
                                latch.await();
                            } catch (InterruptedException e) {
                                // this means we were unsubscribed (Scheduler shut down and interrupts)
                                // ... but we'll pretend we are like many Observables that ignore interrupts
                            }

                            subscriber.onComplete();
                        } catch (Throwable e) {
                            subscriber.onError(e);
                        } finally {
                            doneLatch.countDown();
                        }
                    }
                }).subscribeOn(Schedulers.computation()).subscribe(observer);

        // wait for scheduling
        scheduled.await();
        // trigger unsubscribe
        observer.unsubscribe();
        latch.countDown();
        doneLatch.await();
        assertEquals(0, observer.getOnErrorEvents().size());
        assertEquals(1, observer.getOnCompletedEvents().size());
    }

    @Test
    public void testThrownErrorHandling() {
        TestSubscriber<String> ts = new TestSubscriber<String>();
        Observable.create(new OnSubscribe<String>() {

            @Override
            public void call(Subscriber<? super String> s) {
                throw new RuntimeException("fail");
            }

        }).subscribeOn(Schedulers.computation()).subscribe(ts);
        ts.awaitTerminalEvent(1000, TimeUnit.MILLISECONDS);
        ts.assertTerminalEvent();
    }

    @Test
    public void testOnError() {
        TestSubscriber<String> ts = new TestSubscriber<String>();
        Observable.create(new OnSubscribe<String>() {

            @Override
            public void call(Subscriber<? super String> s) {
                s.onError(new RuntimeException("fail"));
            }

        }).subscribeOn(Schedulers.computation()).subscribe(ts);
        ts.awaitTerminalEvent(1000, TimeUnit.MILLISECONDS);
        ts.assertTerminalEvent();
    }

    public static class SlowScheduler extends Scheduler {
        final Scheduler actual;
        final long delay;
        final TimeUnit unit;

        public SlowScheduler() {
            this(Schedulers.computation(), 2, TimeUnit.SECONDS);
        }

        public SlowScheduler(Scheduler actual, long delay, TimeUnit unit) {
            this.actual = actual;
            this.delay = delay;
            this.unit = unit;
        }

        @Override
        public Worker createWorker() {
            return new SlowInner(actual.createWorker());
        }

        private final class SlowInner extends Worker {

            private final Scheduler.Worker actualInner;

            private SlowInner(Worker actual) {
                this.actualInner = actual;
            }

            @Override
            public void dispose() {
                actualInner.dispose();
            }

            @Override
            public boolean isDisposed() {
                return actualInner.isDisposed();
            }

            @Override
            public Disposable schedule(final Action0 action) {
                return actualInner.schedule(action, delay, unit);
            }

            @Override
            public Disposable schedule(final Action0 action, final long delayTime, final TimeUnit delayUnit) {
                TimeUnit common = delayUnit.compareTo(unit) < 0 ? delayUnit : unit;
                long t = common.convert(delayTime, delayUnit) + common.convert(delay, unit);
                return actualInner.schedule(action, t, common);
            }

        }

    }

    @Test(timeout = 5000)
    public void testUnsubscribeInfiniteStream() throws InterruptedException {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        final AtomicInteger count = new AtomicInteger();
        Observable.create(new OnSubscribe<Integer>() {

            @Override
            public void call(Subscriber<? super Integer> sub) {
                for (int i = 1; !sub.isUnsubscribed(); i++) {
                    count.incrementAndGet();
                    sub.onNext(i);
                }
            }

        }).subscribeOn(Schedulers.newThread()).take(10).subscribe(ts);

        ts.awaitTerminalEventAndUnsubscribeOnTimeout(1000, TimeUnit.MILLISECONDS);
        Thread.sleep(200); // give time for the loop to continue
        ts.assertReceivedOnNext(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        assertEquals(10, count.get());
    }

    @Test
    public void testBackpressureReschedulesCorrectly() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(10);
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(new Observer<Integer>() {

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(Integer t) {
                latch.countDown();
            }

        });
        ts.requestMore(10);
        Observable.range(1, 10000000).subscribeOn(Schedulers.newThread()).take(20).subscribe(ts);
        latch.await();
        Thread t = ts.getLastSeenThread();
        System.out.println("First schedule: " + t);
        assertTrue(t.getName().startsWith("Rx"));
        ts.requestMore(10);
        ts.awaitTerminalEvent();
        System.out.println("After reschedule: " + ts.getLastSeenThread());
        assertEquals(t, ts.getLastSeenThread());
    }

    @Test
    public void testSetProducerSynchronousRequest() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        Observable.just(1, 2, 3).lift(new Operator<Integer, Integer>() {

            @Override
            public Subscriber<? super Integer> call(final Subscriber<? super Integer> child) {
                final AtomicLong requested = new AtomicLong();

                DisposableList dl = new DisposableList();

                child.onSubscribe(new Subscription() {

                    @Override
                    public void request(long n) {
                        if (!requested.compareAndSet(0, n)) {
                            child.onError(new RuntimeException("Expected to receive request before onNext but didn't"));
                        }
                    }

                    @Override
                    public void cancel() {
                        dl.dispose();
                    }

                });

                Subscriber<Integer> parent = new Subscriber<Integer>() {

                    @Override
                    public void onSubscribe(Subscription s) {
                        child.onSubscribe(s);
                        // register Subscription for cancelation
                        dl.add(s);
                    }

                    @Override
                    public void onComplete() {
                        child.onComplete();
                    }

                    @Override
                    public void onError(Throwable e) {
                        child.onError(e);
                    }

                    @Override
                    public void onNext(Integer t) {
                        if (requested.compareAndSet(0, -99)) {
                            child.onError(new RuntimeException("Got values before requested"));
                        }
                    }
                };

                return parent;
            }

        }).subscribeOn(Schedulers.newThread()).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
    }

}
