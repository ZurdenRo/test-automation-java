package com.company.qa.http;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Executes requests against one base URL over a connection pool. The only class that talks to Apache HttpClient. */
final class HttpTransport implements Closeable {

    private final HttpConfig config;
    private final BodyCodec codec;
    private final CloseableHttpClient client;
    private volatile boolean closed;

    HttpTransport(HttpConfig config, BodyCodec codec) {
        this.config = config;
        this.codec = codec;
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(config.maxConnections)
                        .setMaxConnPerRoute(config.maxConnections)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofMilliseconds(config.connectTimeoutMs))
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setRedirectsEnabled(config.followRedirects)
                        .setResponseTimeout(Timeout.ofMilliseconds(config.responseTimeoutMs))
                        .build())
                .build();
    }

    HttpResponse get(String path) {
        return get(HttpRequest.to(path));
    }

    HttpResponse get(HttpRequest request) {
        return send("GET", withoutBody("GET", request));
    }

    HttpResponse post(HttpRequest request) {
        return send("POST", request);
    }

    HttpResponse put(HttpRequest request) {
        return send("PUT", request);
    }

    HttpResponse patch(HttpRequest request) {
        return send("PATCH", request);
    }

    HttpResponse delete(HttpRequest request) {
        return send("DELETE", withoutBody("DELETE", request));
    }

    private static HttpRequest withoutBody(String method, HttpRequest request) {
        if (request.body() != null) {
            throw new IllegalArgumentException(method + " " + request.path() + " does not take a body");
        }
        return request;
    }

    private HttpResponse send(String method, HttpRequest request) {
        String url = config.urlFor(request.path()) + queryString(request.path(), request.query());
        if (closed) {
            throw new HttpTransportException(method + " " + url + " failed: the transport is closed");
        }
        HttpUriRequestBase message = newRequest(method, url);
        try {
            for (Map.Entry<String, String> header : config.headersFor(request.headers()).pairs()) {
                message.addHeader(header.getKey(), header.getValue());
            }
            RequestBody body = request.body();
            if (body != null) {
                // Bytes, not a String entity: Apache would encode a type without charset as ISO-8859-1.
                message.setEntity(new ByteArrayEntity(
                        contentOf(method, url, body).getBytes(StandardCharsets.UTF_8),
                        ContentType.parse(body.contentType())));
            }
            HttpClientContext context = HttpClientContext.create();
            URI requested = message.getUri();
            return client.execute(message, context, response -> new HttpResponse(
                    response.getCode(),
                    response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity()),
                    lastRedirectOr(requested, context),
                    headersOf(response),
                    codec));
        } catch (IOException | URISyntaxException e) {
            throw new HttpTransportException(method + " " + url + " failed: " + e.getMessage(), e);
        }
    }

    private String contentOf(String method, String url, RequestBody body) {
        try {
            return body.content(codec);
        } catch (Exception e) {
            throw new HttpTransportException(method + " " + url + " failed: could not write the "
                    + body.modelTypeName() + " body (" + e.getMessage() + ")", e);
        }
    }

    /** Apache rejects an illegal URL with a raw IllegalArgumentException; callers get ours, naming the URL. */
    private static HttpUriRequestBase newRequest(String method, String url) {
        try {
            return new HttpUriRequestBase(method, URI.create(url));
        } catch (IllegalArgumentException e) {
            throw new HttpTransportException("Invalid URL for " + method + " " + url + ": " + e.getMessage(), e);
        }
    }

    private static String queryString(String path, QueryParams query) {
        if (query.isEmpty()) {
            return "";
        }
        return (path.contains("?") ? "&" : "?") + query.encoded();
    }

    private static Headers headersOf(org.apache.hc.core5.http.HttpResponse response) {
        Headers headers = Headers.empty();
        for (Header header : response.getHeaders()) {
            headers = headers.and(header.getName(), header.getValue());
        }
        return headers;
    }

    private static URI lastRedirectOr(URI requested, HttpClientContext context) {
        RedirectLocations redirects = context.getRedirectLocations();
        if (redirects == null) {
            return requested;
        }
        List<URI> all = redirects.getAll();
        return all.isEmpty() ? requested : all.get(all.size() - 1);
    }

    /** A secret by its name in {@code secret.<name>}, read from the environment variable the service config names. */
    String secret(String name) {
        return config.secrets.get(name);
    }

    @Override
    public void close() {
        closed = true;
        try {
            client.close();
        } catch (IOException e) {
            throw new HttpTransportException("Closing transport failed: " + e.getMessage(), e);
        }
    }
}
