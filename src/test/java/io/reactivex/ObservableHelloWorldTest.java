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

import io.reactivex.subscribers.TestSubscriber;

import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class ObservableHelloWorldTest {

    /**
     * "Hello World" => Most basic form of create/subscribe
     */
    @Test
    public void testCreateSubscribeBasic() {
        Observable<Integer> o = Observable.create(s -> {
            s.onSubscribe(new Subscription() {

                @Override
                public void request(long n) {
                    s.onNext(1);
                    s.onComplete();
                }

                @Override
                public void cancel() {

                }

            });
        });

        TestSubscriber<Integer> ts = new TestSubscriber<>();
        o.subscribe(ts);
        ts.assertSuccessfulOnNextOf(1);
    }

    /**
     * Basic use of 'lift' with a transform.
     */
    @Test
    public void testLift() {
        Observable<Integer> o = Observable.create(s -> {
            s.onSubscribe(new Subscription() {

                @Override
                public void request(long n) {
                    s.onNext(1);
                    s.onComplete();
                }

                @Override
                public void cancel() {

                }

            });
        });

        Observable<String> mapped = o.lift(child -> {
            return new Subscriber<Integer>() {

                @Override
                public void onSubscribe(Subscription s) {
                    child.onSubscribe(s);
                }

                @Override
                public void onNext(Integer t) {
                    child.onNext("transformed_" + t);
                }

                @Override
                public void onError(Throwable t) {
                    child.onError(t);
                }

                @Override
                public void onComplete() {
                    child.onComplete();
                }

            };
        });

        TestSubscriber<String> ts = new TestSubscriber<>();
        mapped.subscribe(ts);
        ts.assertSuccessfulOnNextOf("transformed_1");
    }
}