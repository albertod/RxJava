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

import io.reactivex.Observable;
import io.reactivex.Observable.OnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.Scheduler.Worker;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Subscribes Observers on the specified {@code Scheduler}.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/subscribeOn.png" alt="">
 */
public class OnSubscribeSubscribeOn<T> implements OnSubscribe<T> {

    private final Scheduler scheduler;
    private final Observable<T> o;

    public OnSubscribeSubscribeOn(Observable<T> o, Scheduler scheduler) {
        this.o = o;
        this.scheduler = scheduler;
    }

    @Override
    public void call(Subscriber<? super T> child) {
        final Worker inner = scheduler.createWorker();
        inner.schedule(() -> {
            final Thread t = Thread.currentThread();
            // subscribe up to parent to get Subscription
            ParentSubscription parent = new ParentSubscription(child);
            o.subscribe(parent);

            child.onSubscribe(new Subscription() {

                @Override
                public void request(final long n) {
                    if (Thread.currentThread() == t) {
                        // don't schedule if we're already on the thread (primarily for first setProducer call)
                        // see unit test 'testSetProducerSynchronousRequest' for more context on this
                        parent.request(n);
                    } else {
                        inner.schedule(() -> {
                            parent.request(n);
                        });
                    }
                }

                @Override
                public void cancel() {
                    inner.dispose();
                }

            });

        });
    }

    private class ParentSubscription implements Subscriber<T> {

        Subscription s;
        Subscriber<? super T> child;

        ParentSubscription(Subscriber<? super T> c) {
            this.child = c;
        }

        private void request(long n) {
            s.request(n);
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.s = s;
        }

        @Override
        public void onNext(T t) {
            child.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            child.onError(t);
        }

        @Override
        public void onComplete() {
            child.onComplete();
        }

    }

}
