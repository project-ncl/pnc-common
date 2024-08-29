/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2020 Red Hat, Inc., and individual contributors
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.dto.HeartbeatConfig;
import org.jboss.pnc.api.dto.Request;

import org.jboss.pnc.common.http.PNCHttpClient;

/**
 * Service, which performs regular heartbeat calls using subscription style approach.
 *
 * @author Honza Brázdil &lt;jbrazdil@redhat.com&gt;
 * @author Jakub Bartecek &lt;jbartece@redhat.com&gt;
 * @author Dustin Kut Moy Cheung &lt;dcheung@redhat.com&gt;
 */
@Slf4j
public class HeartbeatScheduler {

    private final Map<String, ScheduledFuture<?>> subscribedRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor;
    private final PNCHttpClient httpClient;

    public HeartbeatScheduler(ScheduledExecutorService executor, PNCHttpClient httpClient) {
        this.executor = executor;
        this.httpClient = httpClient;
    }

    /**
     * Start sending the heartbeat based on the {@link HeartbeatConfig} config.
     * 
     * @param id ID of the heartbeat, used also to unsubscribe it.
     * @param heartbeatConfig Config for the heartbeat.
     */
    public void subscribeRequest(String id, HeartbeatConfig heartbeatConfig) {
        ScheduledFuture<?> beat = executor.scheduleAtFixedRate(
                () -> this.sendHeartbeat(heartbeatConfig.getRequest()),
                0L,
                heartbeatConfig.getDelay(),
                heartbeatConfig.getDelayTimeUnit());

        subscribedRequests.put(id, beat);
    }

    /**
     * Stops heartbeat with given ID.
     *
     * @param id ID of the heartbeat, that was used to start it.
     */
    public void unsubscribeRequest(String id) {
        ScheduledFuture<?> toRemove = subscribedRequests.remove(id);

        if (toRemove != null) {
            toRemove.cancel(false);
        }
    }

    private void sendHeartbeat(Request heartbeatRequest) {
        try {
            httpClient.trySendRequest(heartbeatRequest);
        } catch (RuntimeException e) {
            log.warn("Heartbeat failed with an exception!", e);
        }
    }

}
