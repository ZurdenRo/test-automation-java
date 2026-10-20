package com.company.qa.http;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/** Executes GETs against one base URL over a connection pool. The only class that talks to Apache HttpClient. */
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
        String url = config.urlFor(request.path()) + queryString(request.path(), request.query());
        if (closed) {
            throw new HttpTransportException("GET " + url + " failed: the transport is closed");
        }
        HttpGet get = newGet(url);
        try {
            for (Map.Entry<String, String> header : config.headersFor(request.headers()).pairs()) {
                get.addHeader(header.getKey(), header.getValue());
            }
            HttpClientContext context = HttpClientContext.create();
            URI requested = get.getUri();
            return client.execute(get, context, response -> new HttpResponse(
                    response.getCode(),
                    response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity()),
                    lastRedirectOr(requested, context),
                    headersOf(response),
                    codec));
        } catch (IOException | URISyntaxException e) {
            throw new HttpTransportException("GET " + url + " failed: " + e.getMessage(), e);
        }
    }

    /** Apache rejects an illegal URL with a raw IllegalArgumentException; callers get ours, naming the URL. */
    private static HttpGet newGet(String url) {
        try {
            return new HttpGet(url);
        } catch (IllegalArgumentException e) {
            throw new HttpTransportException("Invalid URL for GET " + url + ": " + e.getMessage(), e);
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
