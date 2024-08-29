/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package org.jboss.pnc.common.http;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.assertj.core.api.Assertions;
import org.jboss.pnc.api.dto.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static org.jboss.pnc.api.constants.HttpHeaders.AUTHORIZATION_STRING;

@WireMockTest
public class PNCHttpClientTest {

    public static final TestConfig CONFIG = new TestConfig();
    public static final Map<String, Object> PAYLOAD = Map.of("foo", "bar", "baz", 14L);

    public static final String PAYLOAD_JSON = "{\"foo\":\"bar\",\"baz\":14}";
    public static final String HEADER_NAME = "MyHeader";
    public static final String HEADER_VALUE = "This is may headerValue";
    public static final String ENDPOINT = "/test-endpoint";
    private static final int MAX_RETRIES = 3;
    private static URI wiremockURI;

    @BeforeAll
    public static void setup(WireMockRuntimeInfo wmRuntimeInfo) {
        wiremockURI = URI.create(wmRuntimeInfo.getHttpBaseUrl());
    }

    private Request createRequest() {
        return Request.builder()
                .uri(wiremockURI.resolve(ENDPOINT))
                .attachment(PAYLOAD)
                .method(Request.Method.POST)
                .header(HEADER_NAME, HEADER_VALUE)
                .build();
    }

    @Test
    public void sendRequest_normally_succeeds() {
        WireMock.stubFor(WireMock.post(ENDPOINT).willReturn(WireMock.ok()));
        PNCHttpClient client = new PNCHttpClient(CONFIG);

        client.trySendRequest(createRequest());
        client.sendRequest(createRequest());

        WireMock.verify(
                2,
                WireMock.postRequestedFor(WireMock.urlEqualTo(ENDPOINT))
                        .withHeader(HEADER_NAME, WireMock.equalTo(HEADER_VALUE))
                        .withRequestBody(WireMock.equalToJson(PAYLOAD_JSON)));
    }

    @Test
    public void sendRequest_withGET_stripsPayload() {
        Map<String, Object> payload = Map.of("payload", "new", "isHere", Boolean.TRUE);

        WireMock.stubFor(WireMock.get(ENDPOINT).willReturn(WireMock.ok()));
        PNCHttpClient client = new PNCHttpClient(CONFIG);

        Request request = Request.builder()
                .uri(wiremockURI.resolve(ENDPOINT))
                .attachment(PAYLOAD)
                .method(Request.Method.GET)
                .header(HEADER_NAME, HEADER_VALUE)
                .build();

        client.trySendRequest(request);
        client.sendRequest(request);
        client.trySendRequest(request, payload);
        client.sendRequest(request, payload);

        WireMock.verify(
                4,
                WireMock.getRequestedFor(WireMock.urlEqualTo(ENDPOINT))
                        .withHeader(HEADER_NAME, WireMock.equalTo(HEADER_VALUE))
                        .withRequestBody(WireMock.absent()));
    }

    @Test
    public void sendRequest_withStatus204_succeeds() {
        WireMock.stubFor(WireMock.post(ENDPOINT).willReturn(WireMock.status(204)));
        PNCHttpClient client = new PNCHttpClient(CONFIG);

        client.trySendRequest(createRequest());
        client.sendRequest(createRequest());

        WireMock.verify(
                2,
                WireMock.postRequestedFor(WireMock.urlEqualTo(ENDPOINT))
                        .withHeader(HEADER_NAME, WireMock.equalTo(HEADER_VALUE))
                        .withRequestBody(WireMock.equalToJson(PAYLOAD_JSON)));
    }

    @Test
    public void sendRequest_withTokenSupplier_sendsTokenInHeader() {
        WireMock.stubFor(WireMock.post(ENDPOINT).willReturn(WireMock.ok()));
        PNCHttpClient client = new PNCHttpClient(CONFIG);
        String token = "token";
        client.setTokenSupplier(() -> token);

        client.trySendRequest(createRequest());
        client.sendRequest(createRequest());

        WireMock.verify(
                2,
                WireMock.postRequestedFor(WireMock.urlEqualTo(ENDPOINT))
                        .withHeader(HEADER_NAME, WireMock.equalTo(HEADER_VALUE))
                        .withHeader(AUTHORIZATION_STRING, WireMock.equalTo("Bearer " + token))
                        .withRequestBody(WireMock.equalToJson(PAYLOAD_JSON)));
    }

    @Test
    public void sendRequest_withPayload_sendsWithPayload() {
        Map<String, Object> payload = Map.of("payload", "new", "isHere", Boolean.TRUE);
        String payloadJson = "{\"payload\":\"new\",\"isHere\":true}";

        WireMock.stubFor(WireMock.post(ENDPOINT).willReturn(WireMock.ok()));
        PNCHttpClient client = new PNCHttpClient(CONFIG);

        client.trySendRequest(createRequest(), payload);
        client.sendRequest(createRequest(), payload);

        WireMock.verify(
                2,
                WireMock.postRequestedFor(WireMock.urlEqualTo(ENDPOINT))
                        .withHeader(HEADER_NAME, WireMock.equalTo(HEADER_VALUE))
                        .withRequestBody(WireMock.equalToJson(payloadJson)));
    }

    @Test
    public void trySendRequest_gets500_willThrowException() {
        WireMock.stubFor(WireMock.post(ENDPOINT).willReturn(WireMock.serverError()));
        PNCHttpClient client = new PNCHttpClient(CONFIG);

        Assertions.assertThatThrownBy(() -> client.trySendRequest(createRequest()))
                .isInstanceOf(PNCHttpClientException.class)
                .hasMessageContainingAll(wiremockURI.toString(), ENDPOINT, "failed with status: 500");

        WireMock.verify(
                1,
                WireMock.postRequestedFor(WireMock.urlEqualTo(ENDPOINT))
                        .withHeader(HEADER_NAME, WireMock.equalTo(HEADER_VALUE))
                        .withRequestBody(WireMock.equalToJson(PAYLOAD_JSON)));
    }

    @Test
    public void sendRequest_gets500Always_willRetryAndThrowAnException() {
        WireMock.stubFor(WireMock.post(ENDPOINT).willReturn(WireMock.serverError()));
        PNCHttpClient client = new PNCHttpClient(CONFIG);

        Assertions.assertThatThrownBy(() -> client.sendRequest(createRequest()))
                .isInstanceOf(PNCHttpClientException.class)
                .hasMessageContainingAll(wiremockURI.toString(), ENDPOINT, "failed with status: 500");

        WireMock.verify(
                MAX_RETRIES + 1,
                WireMock.postRequestedFor(WireMock.urlEqualTo(ENDPOINT))
                        .withHeader(HEADER_NAME, WireMock.equalTo(HEADER_VALUE))
                        .withRequestBody(WireMock.equalToJson(PAYLOAD_JSON)));
    }

    @Test
    public void sendRequest_gets500Once_willRetryAndSucceeds() {
        String state2 = "Return 200 on second try";
        WireMock.stubFor(
                WireMock.post(ENDPOINT)
                        .inScenario("gets500Once")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(WireMock.serverError())
                        .willSetStateTo(state2));

        WireMock.stubFor(
                WireMock.post(ENDPOINT)
                        .inScenario("gets500Once")
                        .whenScenarioStateIs(state2)
                        .willReturn(WireMock.ok()));

        PNCHttpClient client = new PNCHttpClient(CONFIG);

        client.sendRequest(createRequest());

        WireMock.verify(
                2,
                WireMock.postRequestedFor(WireMock.urlEqualTo(ENDPOINT))
                        .withHeader(HEADER_NAME, WireMock.equalTo(HEADER_VALUE))
                        .withRequestBody(WireMock.equalToJson(PAYLOAD_JSON)));
    }

    @Test
    public void trySendRequest_throwsException_throwsException() {
        WireMock.stubFor(WireMock.post(ENDPOINT).willReturn(WireMock.aResponse().withFault(Fault.EMPTY_RESPONSE)));
        PNCHttpClient client = new PNCHttpClient(CONFIG);

        Assertions.assertThatThrownBy(() -> client.trySendRequest(createRequest()))
                .isInstanceOf(PNCHttpClientException.class)
                .hasCauseInstanceOf(IOException.class)
                .hasMessageContainingAll(wiremockURI.toString(), ENDPOINT, "failed with error", "received no bytes");

        WireMock.verify(
                1,
                WireMock.postRequestedFor(WireMock.urlEqualTo(ENDPOINT))
                        .withHeader(HEADER_NAME, WireMock.equalTo(HEADER_VALUE))
                        .withRequestBody(WireMock.equalToJson(PAYLOAD_JSON)));
    }

    @Test
    public void sendRequest_throwsExceptionAlways_willRetryAndThrowAnException() {
        WireMock.stubFor(WireMock.post(ENDPOINT).willReturn(WireMock.aResponse().withFault(Fault.EMPTY_RESPONSE)));

        PNCHttpClient client = new PNCHttpClient(CONFIG);

        Assertions.assertThatThrownBy(() -> client.sendRequest(createRequest()))
                .isInstanceOf(PNCHttpClientException.class)
                .hasCauseInstanceOf(IOException.class)
                .hasMessageContainingAll(wiremockURI.toString(), ENDPOINT, "failed with error", "received no bytes");

        WireMock.verify(
                MAX_RETRIES + 1,
                WireMock.postRequestedFor(WireMock.urlEqualTo(ENDPOINT))
                        .withHeader(HEADER_NAME, WireMock.equalTo(HEADER_VALUE))
                        .withRequestBody(WireMock.equalToJson(PAYLOAD_JSON)));
    }

    @Test
    public void sendRequest_throwsExceptionOnce_willRetryAndSucceeds() {
        String state2 = "Return 200 on second try";
        WireMock.stubFor(
                WireMock.post(ENDPOINT)
                        .inScenario("throwsExceptionOnce")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(WireMock.aResponse().withFault(Fault.EMPTY_RESPONSE))
                        .willSetStateTo(state2));
        WireMock.stubFor(
                WireMock.post(ENDPOINT)
                        .inScenario("throwsExceptionOnce")
                        .whenScenarioStateIs(state2)
                        .willReturn(WireMock.ok()));

        PNCHttpClient client = new PNCHttpClient(CONFIG);
        client.sendRequest(createRequest());

        WireMock.verify(
                2,
                WireMock.postRequestedFor(WireMock.urlEqualTo(ENDPOINT))
                        .withHeader(HEADER_NAME, WireMock.equalTo(HEADER_VALUE))
                        .withRequestBody(WireMock.equalToJson(PAYLOAD_JSON)));
    }

    @AllArgsConstructor
    @NoArgsConstructor
    public static class TestConfig implements PNCHttpClientConfig {
        long connectTimeout = 2000;
        long requestTimeout = 60000;
        boolean forceHTTP11 = false;
        RetryConfig retryConfig = new TestRetryConfig();

        @Override
        public RetryConfig retryConfig() {
            return retryConfig;
        }

        @Override
        public long connectTimeout() {
            return connectTimeout;
        }

        @Override
        public long requestTimeout() {
            return requestTimeout;
        }

        @Override
        public boolean forceHTTP11() {
            return forceHTTP11;
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    public static class TestRetryConfig implements PNCHttpClientConfig.RetryConfig {
        int backoffInitialDelay = 1;
        int backoffMaxDelay = 2;
        int maxRetries = MAX_RETRIES;
        int maxDuration = 20;

        @Override
        public int backoffInitialDelay() {
            return backoffInitialDelay;
        }

        @Override
        public int backoffMaxDelay() {
            return backoffMaxDelay;
        }

        @Override
        public int maxRetries() {
            return maxRetries;
        }

        @Override
        public int maxDuration() {
            return maxDuration;
        }
    }
}
