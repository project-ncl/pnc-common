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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.common.Json;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.jboss.pnc.api.constants.HttpHeaders.AUTHORIZATION_STRING;
import static org.jboss.pnc.api.constants.HttpHeaders.CONTENT_TYPE_STRING;

/**
 * Wrapper around {@link HttpClient} that can handle PNC {@link Request}s.
 */
@Slf4j
public class PNCHttpClient {
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final RetryPolicy<HttpResponse<String>> retryPolicy;
    /**
     * Supplier of authentication token to be used with every request. When set, will set the
     * {@link org.jboss.pnc.api.constants.HttpHeaders#AUTHORIZATION_STRING} header.
     */
    @Setter
    private Supplier<String> tokenSupplier;
    /**
     * Predicate that is used to decide if retries should be ended early, based on the HTTP status received.
     */
    @Setter
    private Predicate<Integer> abortRetriesOnStatus = (_ignored) -> false;

    public PNCHttpClient(PNCHttpClientConfig config) {
        this(Json.newObjectMapper(), config);
    }

    public PNCHttpClient(ObjectMapper objectMapper, PNCHttpClientConfig config) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (config.forceHTTP11()) {
            builder.version(HttpClient.Version.HTTP_1_1);
        }
        this.client = builder.build();
        this.objectMapper = objectMapper;
        this.requestTimeout = config.requestTimeout();

        PNCHttpClientConfig.RetryConfig retryConfig = config.retryConfig();
        retryPolicy = RetryPolicy.<HttpResponse<String>> builder()
                .withBackoff(retryConfig.backoffInitialDelay(), retryConfig.backoffMaxDelay())
                .withMaxRetries(retryConfig.maxRetries())
                .abortOn(this::abortOn)
                .withMaxDuration(retryConfig.maxDuration())
                .onSuccess(e -> log.debug("Request successfully sent, response: {}", e.getResult().statusCode()))
                .onRetry(
                        e -> log.debug(
                                "Retrying (attempt #{}), last exception was: {}",
                                e.getAttemptCount(),
                                e.getLastException().getMessage()))
                .onFailure(e -> log.warn("Request couldn't be sent.", e.getException()))
                .build();
    }

    private boolean abortOn(Throwable throwable) {
        if (throwable instanceof PNCHttpClientException) {
            PNCHttpClientException exception = (PNCHttpClientException) throwable;
            if (exception.getStatus() != null) {
                return abortRetriesOnStatus.test(exception.getStatus());
            }
        }
        return false;
    }

    /**
     * Tries to send the request once, reporting any error immediately.
     *
     * @param request Request to be sent.
     * @throws PNCHttpClientException When there is error sending the request.
     */
    public void trySendRequest(Request request) {
        trySendRequest(request, request.getAttachment());
    }

    /**
     * Tries to send the request once replacing the payload from {@link Request#getAttachment()} with provided payload,
     * reporting any error immediately.
     *
     * @param request Request to be sent.
     * @param payload Payload to be used instead of {@link Request#getAttachment()}
     * @throws PNCHttpClientException When there is error sending the request.
     */
    public void trySendRequest(Request request, Object payload) {
        HttpRequest httpRequest = prepareHttpRequest(request, payload);
        doSendRequest(httpRequest);
    }

    /**
     * Sends the request, retrying according to the retry config if there is any error.
     *
     * @param request Request to be sent.
     * @throws PNCHttpClientException When there is error sending the request.
     */
    public void sendRequest(Request request) {
        sendRequest(request, request.getAttachment());
    }

    /**
     * Send the request replacing the payload from {@link Request#getAttachment()} with provided payload, retrying
     * according to the retry config if there is any error.
     *
     * @param request Request to be sent.
     * @param payload Payload to be used instead of {@link Request#getAttachment()}
     * @throws PNCHttpClientException When there is error sending the request.
     */
    public void sendRequest(Request request, Object payload) {
        HttpRequest httpRequest = prepareHttpRequest(request, payload);
        Failsafe.with(retryPolicy).run(() -> doSendRequest(httpRequest));
    }

    /**
     * Sends the request and returns the full HTTP response (status, headers, body). Retries according to the configured
     * retry policy.
     */
    public HttpResponse<String> sendRequestForResponse(Request request) {
        return sendRequestForResponse(request, request.getAttachment());
    }

    public HttpResponse<String> sendRequestForResponse(Request request, Object payload) {
        HttpRequest httpRequest = prepareHttpRequest(request, payload);
        return Failsafe.with(retryPolicy).get(() -> doSendRequestForResponse(httpRequest));
    }

    private HttpResponse<String> doSendRequestForResponse(HttpRequest httpRequest) {
        log.debug("Sending the request {}", httpRequest);
        try {
            HttpResponse<String> response = client
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.debug("Response status code is: {}", response.statusCode());

            if (response.statusCode() >= 300) {
                // Include a small snippet of the body to aid debugging
                String body = response.body();
                String snippet = body == null ? "" : (body.length() > 500 ? body.substring(0, 500) + "..." : body);

                throw new PNCHttpClientException(
                        "Sending request to " + httpRequest.method() + " " + httpRequest.uri() + " failed with status: "
                                + response.statusCode() + (snippet.isEmpty() ? "" : ", body: " + snippet),
                        response.statusCode());
            }

            return response;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PNCHttpClientException(
                    "Sending request to " + httpRequest.method() + " " + httpRequest.uri() + " failed with error: "
                            + e.getMessage(),
                    e);
        }
    }

    private void doSendRequest(HttpRequest httpRequest) {
        log.debug("Sending the request {}", httpRequest);
        try {
            HttpResponse<Void> response = client.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            log.debug("Response status code is: {}", response.statusCode());
            if (response.statusCode() >= 300) {
                throw new PNCHttpClientException(
                        "Sending request to " + httpRequest.method() + " " + httpRequest.uri() + " failed with status: "
                                + response.statusCode(),
                        response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            throw new PNCHttpClientException(
                    "Sending request to " + httpRequest.method() + " " + httpRequest.uri() + " failed with error: "
                            + e.getMessage(),
                    e);
        }
    }

    private HttpRequest prepareHttpRequest(Request request, Object payload) {
        log.debug("Performing HTTP request with these parameters: {}", request);
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.getUri());
        builder.timeout(requestTimeout);

        switch (request.getMethod()) {
            case POST:
            case PATCH:
            case PUT:
                setMethodWithBody(builder, request.getMethod(), payload);
                break;
            case DELETE:
            case GET:
            case HEAD:
            case OPTIONS:
                builder.method(request.getMethod().name(), HttpRequest.BodyPublishers.noBody());
                break;
            default:
                throw new UnsupportedOperationException("Cannot send " + request.getMethod() + " request.");
        }

        request.getHeaders().forEach(h -> builder.header(h.getName(), h.getValue()));
        if (tokenSupplier != null) {
            builder.setHeader(AUTHORIZATION_STRING, "Bearer " + tokenSupplier.get());
        }
        return builder.build();
    }

    private void setMethodWithBody(HttpRequest.Builder builder, Request.Method method, Object payload) {
        HttpRequest.BodyPublisher bp;

        if (payload == null) {
            bp = HttpRequest.BodyPublishers.noBody();
        } else {
            try {
                bp = HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload));
                builder.header(CONTENT_TYPE_STRING, MediaType.APPLICATION_JSON);
            } catch (JsonProcessingException e) {
                throw new PNCHttpClientException("Failure to parse payload object to JSON", e);
            }
        }

        builder.method(method.name(), bp);
    }
}
