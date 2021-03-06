/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

abstract class AbstractConversionTest {
    public static final String TRAILER_NAME = "foo";
    public static final String TRAILER_VALUE = "bar";
    private final PayloadInfo payloadInfo;

    AbstractConversionTest(final PayloadInfo payloadInfo) {
        this.payloadInfo = payloadInfo;
    }

    void verifyTrailers(Supplier<HttpHeaders> trailersSupplier) {
        if (payloadInfo.mayHaveTrailers()) {
            assertThat("Unexpected trailer with name: " + TRAILER_NAME, trailersSupplier.get().get(TRAILER_NAME),
                    equalTo(TRAILER_VALUE));
        }
    }

    void verifyPayload(Buffer buffer) {
        assertThat("Unexpected payload.", buffer, equalTo(DEFAULT_ALLOCATOR.fromAscii("Hello")));
    }

    static HttpHeaders addTrailers(HttpHeaders trailers) {
        trailers.add(TRAILER_NAME, TRAILER_VALUE);
        return trailers;
    }

    void verifyConvertedStreamingPayload(final Publisher<Object> payloadAndTrailersPublisher) throws Exception {
        Collection<Object> payloadAndTrailers = payloadAndTrailersPublisher.toFuture().get();
        assertThat("Unexpected payload and trailers.", payloadAndTrailers,
                payloadInfo.mayHaveTrailers() ? hasSize(2) : hasSize(1));
        Iterator<Object> iter = payloadAndTrailers.iterator();
        verifyPayload((Buffer) iter.next());
        verifyTrailers(() -> (HttpHeaders) iter.next());
    }

    void verifyAggregatedPayloadInfo(final PayloadInfo aggregatedInfo) {
        assertThat("Aggregated request not safe to aggregate.", aggregatedInfo.isSafeToAggregate(), is(true));
        assertThat("Mismatched trailer info.", aggregatedInfo.mayHaveTrailers(), is(payloadInfo.mayHaveTrailers()));
        assertThat("Mismatched buffer info.", aggregatedInfo.onlyEmitsBuffer(), is(payloadInfo.onlyEmitsBuffer()));
    }

    void verifyPayloadInfo(final PayloadInfo newInfo) {
        assertThat("Mismatched aggregation info.", newInfo.isSafeToAggregate(), is(payloadInfo.isSafeToAggregate()));
        assertThat("Mismatched trailer info.", newInfo.mayHaveTrailers(), is(payloadInfo.mayHaveTrailers()));
        assertThat("Mismatched buffer info.", newInfo.onlyEmitsBuffer(), is(payloadInfo.onlyEmitsBuffer()));
    }

    static class SingleSubscribePublisher extends Publisher<Object> {
        private final AtomicBoolean subscribed = new AtomicBoolean();
        private final PayloadInfo payloadInfo;

        SingleSubscribePublisher(PayloadInfo payloadInfo) {
            this.payloadInfo = payloadInfo;
        }

        @Override
        protected void handleSubscribe(final PublisherSource.Subscriber<? super Object> subscriber) {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            if (subscribed.getAndSet(true)) {
                subscriber.onError(new DuplicateSubscribeException("Duplicate subscribes not allowed.", subscriber));
            } else {
                subscriber.onNext(DEFAULT_ALLOCATOR.fromAscii("Hello"));
                if (payloadInfo.mayHaveTrailers()) {
                    subscriber.onNext(addTrailers(DefaultHttpHeadersFactory.INSTANCE.newTrailers()));
                }
                subscriber.onComplete();
            }
        }
    }
}
