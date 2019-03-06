/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.data.jackson.jersey;

import io.servicetalk.data.jackson.jersey.resources.SingleJsonResources;
import io.servicetalk.http.router.jersey.AbstractJerseyStreamingHttpServiceTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Set;
import javax.ws.rs.core.Application;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static io.servicetalk.data.jackson.jersey.ServiceTalkJacksonSerializerFeature.contextResolverFor;
import static io.servicetalk.data.jackson.jersey.resources.SingleJsonResources.PATH;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.util.Collections.singleton;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;

public class CustomJacksonSerializationProviderTest extends AbstractJerseyStreamingHttpServiceTest {
    public static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return singleton(SingleJsonResources.class);
        }

        @Override
        public Set<Object> getSingletons() {
            return singleton(contextResolverFor(new ObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES)));
        }
    }

    @Override
    protected Application application() {
        return new TestApplication();
    }

    @Override
    protected String testUri(final String path) {
        return PATH + path;
    }

    @Test
    public void postInvalidJsonPojo() {
        sendAndAssertResponse(post("/pojo", "{\"foo\":\"bar\"}", APPLICATION_JSON),
                OK, APPLICATION_JSON, jsonEquals("{\"aString\":\"nullx\",\"anInt\":1}"), __ -> null);
    }
}
