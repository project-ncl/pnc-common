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
package org.jboss.pnc.common.concurrent;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.jboss.pnc.api.dto.HeartbeatConfig;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.common.http.PNCHttpClient;
import org.jboss.pnc.common.http.PNCHttpClientTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@WireMockTest
public class HeartbeatSchedulerTest {
    private static URI wiremockURI;
    public static final String ENDPOINT = "/is-alive";

    private PNCHttpClient client = new PNCHttpClient(new PNCHttpClientTest.TestConfig());

    ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(2);

    @BeforeAll
    public static void setup(WireMockRuntimeInfo wmRuntimeInfo) {
        wiremockURI = URI.create(wmRuntimeInfo.getHttpBaseUrl());
    }

    private HeartbeatConfig createConfig(long delay) {
        Request request = Request.builder().uri(wiremockURI.resolve(ENDPOINT)).method(Request.Method.POST).build();

        return HeartbeatConfig.builder().request(request).delay(delay).delayTimeUnit(TimeUnit.MILLISECONDS).build();
    }

    @Test
    public void shouldSendHeartbeatsAndThenStop() throws InterruptedException {
        WireMock.stubFor(WireMock.post(ENDPOINT).willReturn(WireMock.ok()));
        HeartbeatScheduler heartbeats = new HeartbeatScheduler(executor, client);

        String id = "foobar";

        heartbeats.subscribeRequest(id, createConfig(100));
        TimeUnit.MILLISECONDS.sleep(350);
        heartbeats.unsubscribeRequest(id);
        TimeUnit.MILLISECONDS.sleep(1000);

        // Should send heartbeat at times: 0, 100, 200, 300
        WireMock.verify(4, WireMock.postRequestedFor(WireMock.urlEqualTo(ENDPOINT)));
    }
}
