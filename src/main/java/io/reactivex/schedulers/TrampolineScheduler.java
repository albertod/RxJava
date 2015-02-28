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
package io.reactivex.schedulers;

import io.reactivex.Disposable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.BooleanDisposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.functions.Action0;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Schedules work on the current thread but does not execute immediately. Work is put in a queue and executed
 * after the current unit of work is completed.
 */
public final class TrampolineScheduler extends Scheduler {
    private static final TrampolineScheduler INSTANCE = new TrampolineScheduler();

    /* package */static TrampolineScheduler instance() {
        return INSTANCE;
    }

    @Override
    public Worker createWorker() {
        return new InnerCurrentThreadScheduler();
    }

    /* package accessible for unit tests */TrampolineScheduler() {
    }

    private static class InnerCurrentThreadScheduler extends Scheduler.Worker implements Disposable {

        private static final AtomicIntegerFieldUpdater<InnerCurrentThreadScheduler> COUNTER_UPDATER = AtomicIntegerFieldUpdater.newUpdater(InnerCurrentThreadScheduler.class, "counter");
        @SuppressWarnings("unused")
        volatile int counter;
        private final PriorityBlockingQueue<TimedAction> queue = new PriorityBlockingQueue<TimedAction>();
        private final BooleanDisposable innerDisposable = new BooleanDisposable();
        private final AtomicInteger wip = new AtomicInteger();

        @Override
        public Disposable schedule(Action0 action) {
            return enqueue(action, now());
        }

        @Override
        public Disposable schedule(Action0 action, long delayTime, TimeUnit unit) {
            long execTime = now() + unit.toMillis(delayTime);

            return enqueue(new SleepingAction(action, this, execTime), execTime);
        }

        private Disposable enqueue(Action0 action, long execTime) {
            if (innerDisposable.isDisposed()) {
                return Disposables.disposed();
            }
            final TimedAction timedAction = new TimedAction(action, execTime, COUNTER_UPDATER.incrementAndGet(this));
            queue.add(timedAction);

            if (wip.getAndIncrement() == 0) {
                do {
                    final TimedAction polled = queue.poll();
                    if (polled != null) {
                        polled.action.call();
                    }
                } while (wip.decrementAndGet() > 0);
                return Disposables.disposed();
            } else {
                // queue wasn't empty, a parent is already processing so we just add to the end of the queue
                return Disposables.create(new Action0() {

                    @Override
                    public void call() {
                        queue.remove(timedAction);
                    }

                });
            }
        }

        @Override
        public void dispose() {
            innerDisposable.dispose();
        }

        @Override
        public boolean isDisposed() {
            return innerDisposable.isDisposed();
        }

    }

    private static final class TimedAction implements Comparable<TimedAction> {
        final Action0 action;
        final Long execTime;
        final int count; // In case if time between enqueueing took less than 1ms

        private TimedAction(Action0 action, Long execTime, int count) {
            this.action = action;
            this.execTime = execTime;
            this.count = count;
        }

        @Override
        public int compareTo(TimedAction that) {
            int result = execTime.compareTo(that.execTime);
            if (result == 0) {
                return compare(count, that.count);
            }
            return result;
        }
    }

    // because I can't use Integer.compare from Java 7
    private static int compare(int x, int y) {
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }

}
