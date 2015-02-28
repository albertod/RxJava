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

import io.reactivex.Observable.Operator;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.exceptions.OnErrorThrowable;
import io.reactivex.functions.Func1;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Applies a function of your choosing to every item emitted by an {@code Observable}, and emits the results of
 * this transformation as a new {@code Observable}.
 * <p>
 * <img width="640" height="305" src="https://raw.githubusercontent.com/wiki/ReactiveX/RxJava/images/rx-operators/map.png" alt="">
 */
public final class OperatorMap<T, R> implements Operator<R, T> {

    private final Func1<? super T, ? extends R> transformer;

    public OperatorMap(Func1<? super T, ? extends R> transformer) {
        this.transformer = transformer;
    }

    @Override
    public Subscriber<? super T> call(final Subscriber<? super R> child) {
        return new Subscriber<T>() {

            @Override
            public void onComplete() {
                child.onComplete();
            }

            @Override
            public void onError(Throwable e) {
                child.onError(e);
            }

            @Override
            public void onNext(T t) {
                try {
                    child.onNext(transformer.call(t));
                } catch (Throwable e) {
                    Exceptions.throwIfFatal(e);
                    onError(OnErrorThrowable.addValueAsLastCause(e, t));
                }
            }

            @Override
            public void onSubscribe(Subscription s) {
                child.onSubscribe(s);
            }

        };
    }

}
