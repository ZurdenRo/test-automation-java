package com.company.qa.http;

/**
 * What a service client sends: a path relative to the base URL, plus query params, headers and, for POST, PUT and
 * PATCH, an optional body. Immutable: each method returns a copy. Anything not set here comes from the service client's config; anything set here wins.
 */
public final class HttpRequest {

    private final String path;
    private final QueryParams query;
    private final Headers headers;
    private final RequestBody body;

    private HttpRequest(String path, QueryParams query, Headers headers, RequestBody body) {
        this.path = path;
        this.query = query;
        this.headers = headers;
        this.body = body;
    }

    public static HttpRequest to(String path) {
        return new HttpRequest(path, QueryParams.empty(), Headers.empty(), null);
    }

    /** Returns a copy with one more query parameter; the same name may repeat. */
    public HttpRequest query(String name, String value) {
        return new HttpRequest(path, query.and(name, value), headers, body);
    }

    /** Returns a copy with {@code more} appended after the parameters already set. */
    public HttpRequest query(QueryParams more) {
        return new HttpRequest(path, query.and(more), headers, body);
    }

    /** Returns a copy with one more value for header {@code name}. */
    public HttpRequest header(String name, String value) {
        return new HttpRequest(path, query, headers.and(name, value), body);
    }

    /** Returns a copy with the values of {@code more} added to the headers already set. */
    public HttpRequest headers(Headers more) {
        return new HttpRequest(path, query, headers.and(more), body);
    }

    /** Returns a copy with this body, replacing any set before. Only POST, PUT and PATCH accept one. */
    public HttpRequest body(RequestBody body) {
        return new HttpRequest(path, query, headers, body);
    }

    String path() {
        return path;
    }

    QueryParams query() {
        return query;
    }

    Headers headers() {
        return headers;
    }

    /** {@code null} when the request has no body. */
    RequestBody body() {
        return body;
    }
}
