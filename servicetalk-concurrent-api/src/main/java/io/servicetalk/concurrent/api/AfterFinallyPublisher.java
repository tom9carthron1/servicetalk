/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.concurrent.api;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.util.Objects.requireNonNull;

final class AfterFinallyPublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {

    private final TerminalSignalConsumer doFinally;

    AfterFinallyPublisher(Publisher<T> original, TerminalSignalConsumer doFinally, Executor executor) {
        super(original, executor);
        this.doFinally = requireNonNull(doFinally);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return new AfterFinallyPublisherSubscriber<>(subscriber, doFinally);
    }

    private static final class AfterFinallyPublisherSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;
        private final TerminalSignalConsumer doFinally;

        private static final AtomicIntegerFieldUpdater<AfterFinallyPublisherSubscriber> doneUpdater =
                AtomicIntegerFieldUpdater.newUpdater(AfterFinallyPublisherSubscriber.class, "done");
        @SuppressWarnings("unused")
        private volatile int done;

        AfterFinallyPublisherSubscriber(Subscriber<? super T> original, TerminalSignalConsumer doFinally) {
            this.original = original;
            this.doFinally = doFinally;
        }

        @Override
        public void onSubscribe(Subscription s) {
            original.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    s.request(n);
                }

                @Override
                public void cancel() {
                    try {
                        s.cancel();
                    } finally {
                        if (doneUpdater.compareAndSet(AfterFinallyPublisherSubscriber.this, 0, 1)) {
                            doFinally.cancel();
                        }
                    }
                }
            });
        }

        @Override
        public void onNext(T t) {
            original.onNext(t);
        }

        @Override
        public void onComplete() {
            try {
                original.onComplete();
            } finally {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    doFinally.onComplete();
                }
            }
        }

        @Override
        public void onError(Throwable cause) {
            try {
                original.onError(cause);
            } finally {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    doFinally.onError(cause);
                }
            }
        }
    }
}
