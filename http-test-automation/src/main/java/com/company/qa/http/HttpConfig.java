package com.company.qa.http;

/** The service config of one service client after resolution and validation. */
final class HttpConfig {

    final String baseUrl;
    final int connectTimeoutMs;
    final int responseTimeoutMs;
    final int maxConnections;
    final boolean followRedirects;
    final Headers defaultHeaders;

    HttpConfig(String baseUrl, int connectTimeoutMs, int responseTimeoutMs, int maxConnections,
               boolean followRedirects, Headers defaultHeaders) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.connectTimeoutMs = connectTimeoutMs;
        this.responseTimeoutMs = responseTimeoutMs;
        this.maxConnections = maxConnections;
        this.followRedirects = followRedirects;
        this.defaultHeaders = defaultHeaders;
    }

    /** The base URL and {@code path} joined with exactly one slash, whether or not the path starts with one. */
    String urlFor(String path) {
        if (path.isEmpty()) {
            return baseUrl;
        }
        return path.startsWith("/") ? baseUrl + path : baseUrl + "/" + path;
    }

    /** The headers to send: the defaults, with those of the request replacing them name by name. */
    Headers headersFor(Headers requestHeaders) {
        return defaultHeaders.overriddenBy(requestHeaders);
    }
}
