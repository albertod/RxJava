package io.reactivex;

import io.reactivex.subscribers.TestSubscriber;

import java.util.Arrays;

import org.junit.Test;

public class ObservableFromIterableTest {

    @Test
    public void testJust1() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        Observable.just(1).subscribe(ts);
        ts.assertSuccessfulOnNextOf(1);
    }

    @Test
    public void testJust3() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        Observable.just(1, 2, 3).subscribe(ts);
        ts.assertSuccessfulOnNextOf(1, 2, 3);
    }

    @Test
    public void testFromIterable() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        Observable.from(Arrays.asList(3, 2, 1)).subscribe(ts);
        ts.assertSuccessfulOnNextOf(3, 2, 1);
    }
}
