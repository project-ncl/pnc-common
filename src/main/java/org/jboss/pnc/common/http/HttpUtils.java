package org.jboss.pnc.common.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.jboss.pnc.api.dto.Request;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jboss.pnc.common.Json;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

@Slf4j
public class HttpUtils {
    private static final ObjectMapper objectMapper = Json.newObjectMapper();

    public static void performHttpPostRequest(String uri, String jsonPayload, String authToken)
            throws JsonProcessingException {
        StringEntity entity = new StringEntity(jsonPayload, "UTF-8");
        entity.setContentType(MediaType.APPLICATION_JSON);
        performHttpPostRequest(uri, entity, Optional.ofNullable(authToken));
    }

    public static void performHttpPostRequest(String uri, HttpEntity payload, Optional<String> authToken) {
        log.debug("Sending HTTP POST request to {} with payload {}", uri, payload);

        HttpPost request = new HttpPost(uri);
        request.setEntity(payload);
        if (authToken.isPresent()) {
            request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authToken.get());
        }

        performHttpRequest(URI.create(uri), payload, request);
    }

    public static void performHttpRequest(Request request, Object payload, Optional<String> authToken) {
        URI uri = request.getUri();
        Request.Method method = request.getMethod();
        log.debug("Sending HTTP {} request to {} with payload {}", method, uri, payload);
        HttpEntityEnclosingRequestBase httpRequest;
        switch (method) {
            case POST:
                httpRequest = new HttpPost(uri);
                break;
            case PUT:
                httpRequest = new HttpPut(uri);
                break;
            case PATCH:
                httpRequest = new HttpPatch(uri);
                break;
            default:
                throw new IllegalArgumentException(method + " HTTP method does not support payload.");
        }
        if (payload != null) {
            try {
                StringEntity entity = new StringEntity(objectMapper.writeValueAsString(payload), "UTF-8");
                entity.setContentType(MediaType.APPLICATION_JSON);
                httpRequest.setEntity(entity);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Cannot map payload to JSON", e);
            }
        }
        request.getHeaders().forEach(h -> httpRequest.setHeader(h.getName(), h.getValue()));
        authToken.ifPresent(s -> httpRequest.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + s));
        performHttpRequest(uri, payload, httpRequest);
    }

    private static void performHttpRequest(URI uri, Object payload, HttpUriRequest httpRequest) {
        try (CloseableHttpClient httpClient = HttpUtils.getPermissiveHttpClient()) {
            try (CloseableHttpResponse response = httpClient.execute(httpRequest)) {
                if (isSuccess(response.getStatusLine().getStatusCode())) {
                    log.debug(
                            "HTTP {} request to {} with payload {} sent successfully. Response code: {}",
                            httpRequest.getMethod(),
                            uri,
                            payload,
                            response.getStatusLine().getStatusCode());
                } else {
                    log.error(
                            "Sending HTTP {} request to {} with payload {} failed! " + "Response code: {}, Message: {}",
                            httpRequest.getMethod(),
                            uri,
                            payload,
                            response.getStatusLine().getStatusCode(),
                            response.getEntity().getContent());
                }
            }
        } catch (IOException e) {
            log.error("Error occurred executing the HTTP post request!", e);
        }
    }

    /**
     *
     * NOTE: Be sure to close the HTTP connection after every request!
     *
     * @param retries - int number of retries to execute request in case of failure
     * @return Closeable "permissive" HttpClient instance, ignoring invalid SSL certificates.
     */
    public static CloseableHttpClient getPermissiveHttpClient(int retries) {
        SSLContextBuilder builder = new SSLContextBuilder();
        try {
            builder.loadTrustMaterial(null, (chain, authType) -> true);
        } catch (NoSuchAlgorithmException | KeyStoreException e1) {
            log.error("Error creating SSL Context Builder with trusted certificates.", e1);
        }

        SSLConnectionSocketFactory sslSF = null;
        try {
            sslSF = new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE);
        } catch (KeyManagementException | NoSuchAlgorithmException e1) {
            log.error("Error creating SSL Connection Factory.", e1);
        }

        return HttpClients.custom()
                .setRetryHandler(new DefaultHttpRequestRetryHandler(retries, false))
                .setSSLSocketFactory(sslSF)
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();
    }

    /**
     * @return Closeable "permissive" HttpClient instance, ignoring invalid SSL certificates, using 3 attempts to retry
     *         failed request
     * @see #getPermissiveHttpClient(int)
     */
    public static CloseableHttpClient getPermissiveHttpClient() {
        return getPermissiveHttpClient(3);
    }

    public static boolean isSuccess(int statusCode) {
        return statusCode / 100 == 2;
    }

}
