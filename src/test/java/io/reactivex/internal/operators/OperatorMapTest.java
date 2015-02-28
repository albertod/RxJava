package io.reactivex.internal.operators;

import io.reactivex.Observable;
import io.reactivex.subscribers.TestSubscriber;

import org.junit.Test;

public class OperatorMapTest {

    @Test
    public void testSync() {
        Observable<String> o = Observable.just(1, 2).map(i -> "value_" + i);

        TestSubscriber<String> ts = new TestSubscriber<>();
        o.subscribe(ts);
        ts.assertSuccessfulOnNextOf("value_1", "value_2");
    }
}
