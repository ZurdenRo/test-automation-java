package com.company.qa.http;

import java.io.Closeable;

/**
 * Base of a consumer's service client. The consumer extends it and exposes only its own endpoints; the
 * request methods ({@code get}, {@code post}, {@code put}, {@code patch}, {@code delete}) are protected so that
 * tests call {@code client.getUser(42)}, not raw paths.
 * Create one per suite and close it in teardown: it owns a pool of reusable connections.
 */
public abstract class HttpServiceClient implements Closeable {

    private final HttpTransport transport;

    /** Reads the settings from a {@code .properties} resource on the classpath, named by the consumer. */
    protected HttpServiceClient(String configResource) {
        this(new HttpTransportBuilder().configResource(configResource));
    }

    /** For a client that needs more than a config file: a body codec, an explicit base URL. */
    protected HttpServiceClient(HttpTransportBuilder builder) {
        this.transport = builder.build();
    }

    /** Shortcut for a request that sets nothing but its path. */
    protected HttpResponse get(String path) {
        return get(HttpRequest.to(path));
    }

    /** @throws IllegalArgumentException if the request has a body: GET does not take one */
    protected HttpResponse get(HttpRequest request) {
        return transport.get(request);
    }

    /** Shortcut for a POST without a body or anything else but its path. */
    protected HttpResponse post(String path) {
        return post(HttpRequest.to(path));
    }

    protected HttpResponse post(HttpRequest request) {
        return transport.post(request);
    }

    /** Shortcut for a PUT without a body or anything else but its path. */
    protected HttpResponse put(String path) {
        return put(HttpRequest.to(path));
    }

    protected HttpResponse put(HttpRequest request) {
        return transport.put(request);
    }

    /** Shortcut for a PATCH without a body or anything else but its path. */
    protected HttpResponse patch(String path) {
        return patch(HttpRequest.to(path));
    }

    protected HttpResponse patch(HttpRequest request) {
        return transport.patch(request);
    }

    /** Shortcut for a request that sets nothing but its path. */
    protected HttpResponse delete(String path) {
        return delete(HttpRequest.to(path));
    }

    /** @throws IllegalArgumentException if the request has a body: DELETE does not take one */
    protected HttpResponse delete(HttpRequest request) {
        return transport.delete(request);
    }

    /**
     * A secret (a token, a user, a password) by name: {@code secret("api.token")} reads the environment variable that
     * {@code secret.api.token} names in the service config, so each environment's config can point at its own
     * variable. Use it to build a request; never log it.
     */
    protected String secret(String name) {
        return transport.secret(name);
    }

    /** Closes the connection pool. */
    @Override
    public void close() {
        transport.close();
    }
}
