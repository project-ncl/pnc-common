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

import io.smallrye.config.WithDefault;

import java.time.Duration;

public interface PNCHttpClientConfig {
    RetryConfig retryConfig();

    /**
     * The connect timeout. In the case where a new connection needs to be established, if the connection cannot be
     * established within the given duration, it will fail. Defaults to 30 seconds.
     */
    @WithDefault("PT30s")
    Duration connectTimeout();

    /**
     * A timeout for requests. If the response is not received within the specified timeout then the request will fail.
     * Defaults to 5 minutes.
     */
    @WithDefault("PT5m")
    Duration requestTimeout();

    /**
     * If enabled, will set the client to use only HTTP version 1.1. Defaults to false.
     */
    @WithDefault("false")
    boolean forceHTTP11();

    interface RetryConfig {
        /**
         * The initial delay between retries, exponentially backing off to the maxDelay and multiplying consecutive
         * delays by a factor of 2. Defaults to 1 second.
         */
        @WithDefault("PT1s")
        Duration backoffInitialDelay();

        /**
         * The max delay between retries, exponentially backing off from the initial delay and multiplying consecutive
         * delays by a factor of 2. Defaults to 1 minute.
         */
        @WithDefault("PT60s")
        Duration backoffMaxDelay();

        /**
         * The max number of retries to perform when an execution attempt fails. -1 indicates no limit. Defaults to -1
         * (no limit, will be limited by {@link RetryConfig#maxDuration()}).
         */
        @WithDefault("-1")
        int maxRetries();

        /**
         * The max duration to perform retries for, else the execution will be failed. This setting will not disable max
         * retries. A max retries limit can be disabled via {@link RetryConfig#maxRetries()} = -1. Defaults to 20
         * minutes.
         */
        @WithDefault("PT20m")
        Duration maxDuration();
    }

}
